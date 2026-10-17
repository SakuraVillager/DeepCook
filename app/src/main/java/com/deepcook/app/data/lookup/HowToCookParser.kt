package com.deepcook.app.data.lookup

import com.deepcook.app.core.model.DraftIngredient
import com.deepcook.app.core.model.DraftStep
import com.deepcook.app.core.model.RecipeDraft

/**
 * Turns a HowToCook markdown page into an editable [RecipeDraft].
 *
 * The upstream pages are written by hand and vary: dish names appear in the title line as
 * "# 菜名 的做法", amounts live in a "计算" section that repeats them with a formula, steps are a
 * numbered list whose sub-bullets are the "watch out" hints. Everything here is best effort — the
 * editor shows the result for the user to fix, so a missing section degrades to an empty field
 * instead of failing the lookup.
 */
object HowToCookParser {
    private val BULLET = Regex("""^\s*[-*+]\s+(.*)$""")
    /** Only a step that starts at the left margin is a new step; nested lists belong to it. */
    private val NUMBERED = Regex("""^\d+[.、)．]\s*(.*)$""")
    private val INDENTED_NUMBERED = Regex("""^\s{2,}\d+[.、)．]\s*(.*)$""")
    private val INDENTED_BULLET = Regex("""^\s{2,}[-*+]\s+(.*)$""")
    private val DIFFICULTY = Regex("""预估烹饪难度[：:]\s*(★+)""")
    private val MINUTES = Regex("""(\d+)\s*分钟""")
    private val IMAGE = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
    /** Where an amount starts inside a "计算" line: "肉蟹 1 只…" or "盐 1.5-2g". */
    private val AMOUNT_START = Regex("""[\d０-９]|适量|少许|半""")

    /** Groups are directory names upstream; each maps to one of our dish-style categories. */
    private val GROUP_CATEGORY = mapOf(
        "aquatic" to "海鲜",
        "meat_dish" to "肉类",
        "vegetable_dish" to "素食",
        "soup" to "汤羹",
        "staple" to "主食",
        "breakfast" to "早餐",
        "dessert" to "烘焙甜点",
        "drink" to "其他",
        "condiment" to "其他",
        "semi-finished" to "其他",
    )

    private val INGREDIENT_SECTIONS = listOf("必备原料", "原料", "食材", "用料")
    private val AMOUNT_SECTIONS = listOf("计算", "用量")
    private val STEP_SECTIONS = listOf("操作", "步骤", "做法")
    private val NOTE_SECTIONS = listOf("附加内容", "注意", "小贴士")

    /**
     * @return the draft, or null when the page has no recognisable title, ingredient list or
     *   step list — in which case the caller reports "not found" rather than prefilling junk.
     */
    fun parse(markdown: String, group: String, sourceUrl: String): RecipeDraft? {
        val lines = markdown.replace("\r\n", "\n").split('\n')
        val title = lines
            .firstOrNull { it.startsWith("# ") && !it.startsWith("## ") }
            ?.removePrefix("# ")
            ?.let(::cleanTitle)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val (preamble, sections) = splitSections(lines)
        val ingredients = parseIngredients(sections)
        if (ingredients.isEmpty()) return null
        val steps = parseSteps(sections)
        if (steps.isEmpty()) return null

        val amounts = parseAmounts(sections)
        val description = preamble
            .asSequence()
            .map(::clean)
            .filter { it.isNotBlank() }
            // Headings come first in the preamble; they are not the introduction.
            .filterNot { it.startsWith("#") || it.startsWith("预估") }
            .firstOrNull()
            .orEmpty()

        return RecipeDraft(
            title = title,
            region = "中式",
            categories = listOfNotNull(GROUP_CATEGORY[group] ?: "家常菜"),
            minutes = MINUTES.find(description)?.groupValues?.get(1)?.toIntOrNull(),
            difficulty = DIFFICULTY.find(markdown)?.groupValues?.get(1)?.length,
            servings = "1 人份",
            description = description,
            ingredients = ingredients.map { name ->
                DraftIngredient(name = name, amount = amountFor(name, amounts))
            },
            steps = steps,
            notes = parseNotes(sections),
            cover = null,
            sourceUrl = sourceUrl,
            sourceName = "HowToCook",
            prefilled = true,
        )
    }

    private data class Section(val title: String, val lines: List<String>)

    private fun splitSections(lines: List<String>): Pair<List<String>, List<Section>> {
        val preamble = mutableListOf<String>()
        val sections = mutableListOf<Section>()
        var title: String? = null
        var body = mutableListOf<String>()
        for (line in lines) {
            if (line.startsWith("## ")) {
                val finished = title
                if (finished != null) sections += Section(finished, body)
                title = line.removePrefix("## ").trim()
                body = mutableListOf()
            } else if (title == null) {
                preamble += line
            } else {
                body += line
            }
        }
        title?.let { sections += Section(it, body) }
        return preamble to sections
    }

    private fun parseIngredients(sections: List<Section>): List<String> {
        val section = sections.firstOrNull { section ->
            INGREDIENT_SECTIONS.any { section.title.contains(it) }
        } ?: return emptyList()
        val names = mutableListOf<String>()
        var pending: String? = null
        fun flush() {
            pending?.let { item ->
                val name = clean(item)
                if (name.isNotBlank()) names += name
            }
            pending = null
        }
        for (line in section.lines) {
            val bullet = BULLET.matchEntire(line)
            when {
                // A deeper bullet belongs to the item above it, not to a new ingredient.
                INDENTED_BULLET.matchEntire(line) != null -> flush()
                bullet != null -> {
                    flush()
                    pending = bullet.groupValues[1]
                }
                line.isBlank() -> flush()
                pending != null -> pending = "$pending ${line.trim()}"
            }
        }
        flush()
        return names.distinct()
    }

    /**
     * Reads the "计算" section, which upstream writes two ways: `西红柿 = 1 个 * 份数` on newer
     * pages and `肉蟹 1 只（大约 300g） * 份数` on older ones. Both reduce to a (name, amount) pair;
     * the name is matched against the ingredient list afterwards because shorthand differs
     * ("肉蟹" for "青蟹（别称：肉蟹）").
     */
    private fun parseAmounts(sections: List<Section>): List<Pair<String, String>> {
        val section = sections.firstOrNull { section ->
            AMOUNT_SECTIONS.any { section.title.contains(it) }
        } ?: return emptyList()
        return section.lines.mapNotNull { line ->
            val body = BULLET.matchEntire(line)?.groupValues?.get(1) ?: return@mapNotNull null
            // Drop the "× 份数" formula that upstream appends to every amount.
            val trimmed = body.substringBefore('*').substringBefore('×').let(::clean)
            if (trimmed.isBlank()) return@mapNotNull null
            val equals = trimmed.indexOf('=').takeIf { it >= 0 } ?: trimmed.indexOf('＝')
            if (equals >= 0) {
                val name = trimmed.substring(0, equals).let(::clean)
                val amount = trimmed.substring(equals + 1).let(::clean)
                return@mapNotNull if (name.isNotBlank() && amount.isNotBlank()) name to amount else null
            }
            // No separator: the amount starts at the first digit ("肉蟹 1 只", "盐 1.5-2g").
            val start = AMOUNT_START.find(trimmed)?.range?.first ?: return@mapNotNull null
            val name = trimmed.substring(0, start).let(::clean)
            val amount = trimmed.substring(start).let(::clean)
            if (name.isNotBlank() && amount.isNotBlank()) name to amount else null
        }
    }

    /** Pairs an ingredient name with its calculated amount, allowing for aliases and shorthand. */
    private fun amountFor(ingredient: String, amounts: List<Pair<String, String>>): String {
        val wanted = aliasesOf(ingredient)
        amounts.firstOrNull { (name, _) ->
            val candidate = canonicalName(name)
            wanted.any { alias -> candidate == alias || candidate.contains(alias) || alias.contains(candidate) }
        }?.let { return it.second }
        return ""
    }

    /** "青蟹（别称：肉蟹）" answers to both 青蟹 and 肉蟹. */
    private fun aliasesOf(ingredient: String): List<String> {
        val base = canonicalName(ingredient.substringBefore('（').substringBefore('('))
        val inside = ingredient
            .substringAfter('（', "")
            .substringBefore('）')
            .ifEmpty { ingredient.substringAfter('(', "").substringBefore(')') }
            .replace("别称", "")
            .replace("别名", "")
        val extras = inside
            .split('：', ':', '，', ',', '、')
            .map(::canonicalName)
            .filter(String::isNotBlank)
        return (listOf(base) + extras).filter(String::isNotBlank)
    }

    private fun canonicalName(raw: String): String = raw
        .filter { !it.isWhitespace() }
        .lowercase()

    private fun parseSteps(sections: List<Section>): List<DraftStep> {
        val section = sections.firstOrNull { section ->
            STEP_SECTIONS.any { section.title.contains(it) }
        } ?: return emptyList()
        val steps = mutableListOf<DraftStep>()
        var text = StringBuilder()
        val cues = mutableListOf<String>()
        // Long dishes group their steps under "### 前期准备" style headings; that heading is the
        // best title the next step can get.
        var pendingHeading: String? = null
        var stepHeading: String? = null

        fun flush() {
            val body = text.toString().trim()
            if (body.isNotEmpty()) {
                // Upstream uses two shapes for sub-items: a warning under a step ("don't wash the
                // wok"), or the actual instructions under a lead-in line ("食材处理：" followed by
                // bullets). Only the lead-in form carries the content, so it becomes body text.
                val leadIn = body.endsWith("：") || body.endsWith(":")
                val detail = cues.joinToString("；")
                steps += DraftStep(
                    title = stepHeading ?: stepTitle(body, steps.size),
                    text = if (leadIn && detail.isNotEmpty()) "$body $detail" else body,
                    cue = if (leadIn) "" else detail,
                )
            }
            text = StringBuilder()
            cues.clear()
            stepHeading = null
        }

        for (line in section.lines) {
            val numbered = NUMBERED.matchEntire(line)
            val nested = INDENTED_NUMBERED.matchEntire(line)
            val bullet = INDENTED_BULLET.matchEntire(line)
            when {
                line.startsWith("#") -> {
                    flush()
                    pendingHeading = clean(line.trimStart('#')).trim().takeIf { it.isNotBlank() }
                }
                numbered != null -> {
                    flush()
                    stepHeading = pendingHeading
                    pendingHeading = null
                    text.append(clean(numbered.groupValues[1]))
                }
                bullet != null -> cues += clean(bullet.groupValues[1])
                // A numbered sub-list is a detail of the step above, not a step of its own.
                nested != null -> {
                    val detail = clean(nested.groupValues[1])
                    if (detail.isNotBlank()) text.append("；").append(detail)
                }
                line.isBlank() -> Unit
                text.isEmpty() -> text.append(clean(line))
                else -> text.append(' ').append(clean(line))
            }
        }
        flush()
        return steps
    }

    private fun parseNotes(sections: List<Section>): List<String> {
        val section = sections.firstOrNull { section ->
            NOTE_SECTIONS.any { section.title.contains(it) }
        } ?: return emptyList()
        return section.lines
            .mapNotNull { BULLET.matchEntire(it)?.groupValues?.get(1) }
            .map(::clean)
            .filter { it.isNotBlank() && !it.startsWith("如果您") }
            .distinct()
    }

    /** A short lead-in for the step title: upstream steps have body text only. */
    private fun stepTitle(text: String, index: Int): String {
        val lead = text
            .substringBefore('。')
            .substringBefore('，')
            .substringBefore('、')
            .removeSuffix("：")
            .removeSuffix(":")
            .trim()
        return lead.take(12).ifBlank { "第 ${index + 1} 步" }
    }

    private fun cleanTitle(raw: String): String = clean(raw)
        .removeSuffix("的做法")
        .removeSuffix("做法")
        .trim()

    private fun clean(raw: String): String = raw
        // Images first: "[alt](x.jpg)" would otherwise leave a stray "!" behind as text.
        .replace(IMAGE, "")
        .replace(LINK, "$1")
        .replace("`", "")
        .replace("**", "")
        .replace("~~", "")
        .trim()
}
