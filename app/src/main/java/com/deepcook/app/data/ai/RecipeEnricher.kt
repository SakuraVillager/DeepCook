package com.deepcook.app.data.ai

import com.deepcook.app.core.model.AiOverlay
import com.deepcook.app.core.model.Recipe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fills the fields the bundled recipe left empty (minutes, difficulty, servings, "适量" amounts)
 * with one model call, cached per dish. The output is validated strictly: anything out of range,
 * keyed to an unknown ingredient, or wrapped in prose is dropped instead of shown.
 */
class RecipeEnricher(private val gateway: AiGateway) {
    suspend fun enrich(recipe: Recipe, now: Long = System.currentTimeMillis()): AiOverlay? {
        val targets = missingFields(recipe)
        if (targets.isEmpty()) return null
        val messages = listOf(
            AiMessage.system(SYSTEM),
            AiMessage.user(prompt(recipe, targets)),
        )
        val raw = gateway.complete(messages) ?: return null
        return parse(recipe, raw, now)
    }

    /** Human-readable list of what is missing; empty means no call is needed. */
    fun missingFields(recipe: Recipe): List<String> = buildList {
        if (recipe.minutes == null) add("用时（分钟）")
        if (recipe.difficulty == null) add("难度（1-5）")
        if (recipe.servings.isBlank() || recipe.servings.trim() == "无") add("份量")
        recipe.ingredients.forEach { ingredient ->
            if (EnrichmentRules.isPlaceholder(ingredient.amount)) add("材料 ${ingredient.id}（${ingredient.name}）的用量")
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private const val SYSTEM =
            "你是菜谱资料整理助手。只输出一个 JSON 对象，不要解释，不要 markdown 代码块。"

        private fun prompt(recipe: Recipe, targets: List<String>): String = buildString {
            appendLine("菜名：${recipe.title}（${recipe.region}）")
            appendLine(
                "材料：" + recipe.ingredients.joinToString("；") { "${it.id}=${it.name}（${it.amount.ifBlank { "未写" }}）" },
            )
            appendLine("步骤：" + recipe.steps.take(10).joinToString("；") { it.title })
            appendLine("需要补全：")
            targets.forEach { appendLine("- $it") }
            appendLine(
                """只输出 JSON：{"minutes": 整数或 null, "difficulty": 1-5 或 null, """ +
                    """"servings": "如 2 人份" 或 null, "amounts": {"材料id": "用量"}}""",
            )
            appendLine("不确定的字段填 null，不要编造；只补上面列出的缺失项。")
        }

        fun parse(recipe: Recipe, raw: String, now: Long): AiOverlay? =
            runCatching { parseUnsafe(recipe, raw, now) }.getOrNull()

        private fun parseUnsafe(recipe: Recipe, raw: String, now: Long): AiOverlay? {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val element = json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject

            val minutes = element["minutes"]?.jsonPrimitive?.intOrNull
                ?.takeIf { it in 1..600 }
                ?.takeIf { recipe.minutes == null }
            val difficulty = element["difficulty"]?.jsonPrimitive?.intOrNull
                ?.takeIf { it in 1..5 }
                ?.takeIf { recipe.difficulty == null }
            val servings = element["servings"]?.jsonPrimitive?.content?.trim()
                ?.takeIf { it.isNotEmpty() && it != "无" && it != "null" && it.length <= 20 }
                ?.takeIf { recipe.servings.isBlank() || recipe.servings.trim() == "无" }

            val placeholders = recipe.ingredients
                .filter { EnrichmentRules.isPlaceholder(it.amount) }
                .associateBy { it.id }
            val amounts = buildMap {
                val obj = element["amounts"]?.jsonObject ?: return@buildMap
                obj.forEach { (id, value) ->
                    if (id !in placeholders) return@forEach
                    val text = value.jsonPrimitive.content.trim()
                    if (text.isEmpty() || text == "null" || text.length > 20) return@forEach
                    if (EnrichmentRules.isPlaceholder(text)) return@forEach
                    put(id, text)
                }
            }

            if (minutes == null && difficulty == null && servings == null && amounts.isEmpty()) return null
            return AiOverlay(
                recipeId = recipe.id,
                minutes = minutes,
                difficulty = difficulty,
                servings = servings,
                amounts = amounts,
                fetchedAt = now,
            )
        }
    }
}

object EnrichmentRules {
    private val Placeholders = setOf("", "无", "适量", "少许", "少量", "若干")

    fun isPlaceholder(amount: String?): Boolean = amount == null || amount.trim() in Placeholders
}
