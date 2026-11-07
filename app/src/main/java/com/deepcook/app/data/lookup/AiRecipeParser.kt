package com.deepcook.app.data.lookup

import com.deepcook.app.core.model.AI_DRAFT_SOURCE
import com.deepcook.app.core.model.DraftIngredient
import com.deepcook.app.core.model.DraftStep
import com.deepcook.app.core.model.NO_CONTENT
import com.deepcook.app.core.model.RecipeCategories
import com.deepcook.app.core.model.RecipeDraft
import com.deepcook.app.core.model.RecipeRegions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Turns one model reply about a dish into an editable draft.
 *
 * The model is trusted for shape, not for numbers: a mass or volume amount (克 / g / ml / 毫升 /
 * 斤 / 两 …) is thrown away, because a made-up "300 克" is exactly the kind of error the user would
 * believe. Counts and qualitative amounts ("2 个", "适量") survive. Anything else outside the
 * app's own whitelists is dropped or replaced, so a bad reply degrades to an empty field instead
 * of an unsaveable form.
 */
object AiRecipeParser {
    internal val SYSTEM: String = """
        你是菜谱整理助手。用户给一个菜名，你按常见家常做法写一份简洁菜谱。
        只输出一个 JSON 对象，不要解释，不要 markdown 代码块。

        硬性要求：
        - 材料 4–12 项，只写名称；每项的 amount 一律写空字符串 ""，用量由用户自己填。
        - 不要在任何地方写克数、毫升数、斤两；正文里也不要出现具体用量。
        - 步骤 3–8 个：title 不超过 10 个字，text 一句话说清怎么做（不超过 60 字），cue 写观察要点或空字符串。
        - region 只能取：中式、日式、韩式、美式、欧式、东南亚、其他。
        - categories 从这些里选 1–3 个：家常菜、快手菜、素食、肉类、海鲜、汤羹、主食、面食、烘焙甜点、早餐、沙拉、甜点、饮品、酱料、半成品、国际菜、炖菜、烘焙。
        - minutes 是整数分钟（1–1440），difficulty 是 1–5，servings 写成「2 人份」这种。
        - 不知道的字段填 null 或空字符串，不要编。
        - 如果这不是一道菜、或者你完全不知道它，只输出 {"found": false}。

        输出结构：
        {"found": true, "description": "一句话简介", "region": "中式", "categories": ["家常菜"],
         "minutes": 30, "difficulty": 2, "servings": "2 人份",
         "ingredients": [{"name": "五花肉", "amount": ""}],
         "steps": [{"title": "切肉", "text": "五花肉切成薄片。", "cue": ""}],
         "notes": ["一句话提醒"]}
    """.trimIndent()

    /** The only user turn: the typed name. */
    fun prompt(name: String): String = "菜名：${name.trim()}"

    private const val MAX_INGREDIENTS = 14
    private const val MAX_STEPS = 12
    private const val MAX_NOTES = 4

    private val MassOrVolume = Regex("""\d\s*(克|千克|g|G|ml|ML|毫升|升|斤|两|磅|盎司|l|L)""")

    private val json = Json { ignoreUnknownKeys = true }

    /** @return a saveable draft, or null when the reply is a refusal or too thin to use. */
    fun parse(name: String, raw: String): RecipeDraft? = runCatching { parseUnsafe(name, raw) }.getOrNull()

    private fun parseUnsafe(name: String, raw: String): RecipeDraft? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val element = json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
        if (element.boolean("found") == false) return null

        val ingredients = element["ingredients"]?.jsonArray.orEmpty()
            .mapNotNull { row ->
                val item = row as? JsonObject ?: return@mapNotNull null
                val ingredientName = item.text("name")?.take(30) ?: return@mapNotNull null
                DraftIngredient(name = ingredientName, amount = safeAmount(item.text("amount")))
            }
            .distinctBy { it.name }
            .take(MAX_INGREDIENTS)
        if (ingredients.isEmpty()) return null

        val steps = element["steps"]?.jsonArray.orEmpty()
            .mapNotNull { row ->
                val item = row as? JsonObject ?: return@mapNotNull null
                val text = item.text("text")?.take(200) ?: return@mapNotNull null
                DraftStep(
                    title = item.text("title")?.take(24).orEmpty(),
                    text = text,
                    cue = item.text("cue")?.take(80).orEmpty(),
                )
            }
            .take(MAX_STEPS)
        if (steps.isEmpty()) return null

        val region = element.text("region")?.takeIf { it in RecipeRegions && it != NO_CONTENT } ?: "中式"
        val categories = element["categories"]?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it in RecipeCategories }
            .distinct()
            .take(3)
            .ifEmpty { listOf("家常菜") }
        val notes = element["notes"]?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it.isNotEmpty() }
            .map { it.take(60) }
            .distinct()
            .take(MAX_NOTES)

        return RecipeDraft(
            title = name.trim(),
            region = region,
            categories = categories,
            minutes = element.int("minutes")?.takeIf { it in 1..1440 },
            difficulty = element.int("difficulty")?.takeIf { it in 1..5 },
            servings = element.text("servings")?.take(20).orEmpty(),
            description = element.text("description")?.take(240).orEmpty(),
            ingredients = ingredients,
            steps = steps,
            notes = notes,
            // No page was fetched, and the source line has to say so: this is a model's draft.
            sourceUrl = null,
            sourceName = AI_DRAFT_SOURCE,
            prefilled = true,
        )
    }

    /** Keeps a count or "适量"; drops anything the model made up in mass or volume. */
    private fun safeAmount(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > 12) return ""
        if (MassOrVolume.containsMatchIn(text)) return ""
        return text
    }

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.int(key: String): Int? = text(key)?.toIntOrNull()

    private fun JsonObject.boolean(key: String): Boolean? = text(key)?.toBooleanStrictOrNull()
}
