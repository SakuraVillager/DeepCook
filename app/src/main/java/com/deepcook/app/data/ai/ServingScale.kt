package com.deepcook.app.data.ai

import java.util.Locale
import com.deepcook.app.core.model.Recipe
import kotlin.math.abs
import kotlin.math.round

/** Local quantity scaling shared by detail, preparation, reading and cooking views. */
object ServingScale {
    private val ChineseNumeral = mapOf(
        '一' to 1.0, '两' to 2.0, '二' to 2.0, '三' to 3.0, '四' to 4.0, '五' to 5.0,
        '六' to 6.0, '七' to 7.0, '八' to 8.0, '九' to 9.0, '十' to 10.0,
    )

    private val Fractions = listOf(
        0.125 to "1/8",
        0.25 to "1/4",
        1.0 / 3 to "1/3",
        0.5 to "1/2",
        2.0 / 3 to "2/3",
        0.75 to "3/4",
    )

    /** How many servings the printed amounts are for; null when the field carries no number. */
    fun parse(servings: String): Double? {
        val match = Regex("($Numeric)(?:\\s*[~～\\-—–至到]\\s*$Numeric)?\\s*(?:人|份)").find(label(servings)) ?: return null
        return value(match.groupValues[1])?.takeIf { it in 0.5..100.0 }
    }

    /** The printed serving text without the "原始份量 ·" marker imported recipes carry. */
    fun label(servings: String): String =
        servings.trim().removePrefix("原始份量").trim(' ', '·', ':', '：', '—', '-').trim()

    /** Pickable serving counts for one dish: the usual table sizes plus whatever it already says. */
    fun options(base: Double): List<Int> =
        (listOf(1, 2, 3, 4, 6, 8) + base.toInt()).distinct().filter { it > 0 }.sorted()

    fun factor(base: Double, target: Int): Double = if (base <= 0.0) 1.0 else target / base

    /**
     * Scales one printed amount. Returns [amount] untouched whenever scaling it would be a guess,
     * so the screen can show the result without a second "was this scaled?" flag.
     */
    fun scale(amount: String, factor: Double, target: Int? = null): String {
        val relative = Regex("""[×*]\s*(?:份数|人数|n\b)""", RegexOption.IGNORE_CASE)
        return if (target != null && relative.containsMatchIn(amount)) scaleText(relative.replace(amount, "").trim(), target.toDouble(), amount = true)
            else scaleText(amount, factor, amount = true)
    }

    private val Numeric = """(?:\d+(?:\.\d+)?(?:\s*/\s*\d+)?|半|[一二两三四五六七八九十百千]+)"""
    private val Units = "千克|公斤|毫克|毫升|大汤匙|小茶匙|汤匙|茶匙|大勺|小勺|kg|mg|ml|克|斤|两|升|g|l|勺|杯|碗|滴|撮|片|瓣|根|枚|个|只|条|颗|粒|块|盒|罐|袋|包|张"
    private val Measured = Regex("""(?<![\d./])($Numeric)(\s*[~～\-—–至到]\s*($Numeric))?(\s*)($Units)(?![a-zA-Z])""", RegexOption.IGNORE_CASE)
    private val Mass = Regex("千克|公斤|毫克|毫升|kg|mg|ml|克|斤|两|升|g|l|大汤匙|小茶匙|汤匙|茶匙|大勺|小勺|勺|杯|碗|滴|撮", RegexOption.IGNORE_CASE)

    /** Only ingredient quantities change; times, temperatures, ratios and cut sizes stay printed. */
    fun scaleText(text: String, factor: Double, names: List<String> = emptyList(), amount: Boolean = false): String {
        if (!factor.isFinite() || factor <= 0 || abs(factor - 1) < .0001) return text
        if (amount && listOf("份数", "人数", "斤数", "%", "％", "×", "*", "按口味").any { it in text }) return text
        if (amount && Regex(Numeric).matches(text.trim())) return value(text.trim())?.let { format(it * factor) } ?: text
        return Measured.replace(text) { match ->
            val first = match.groupValues[1]
            val range = match.groupValues[2]
            val second = match.groupValues[3]
            val space = match.groupValues[4]
            val unit = match.groupValues[5]
            val before = text.substring((match.range.first - 12).coerceAtLeast(0), match.range.first)
            val after = text.substring(match.range.last + 1, (match.range.last + 15).coerceAtMost(text.length))
            if (!amount) {
                if (Regex("""每\s*$|分成\s*$|切成\s*$|切为\s*$|厚\s*$|长\s*$|宽\s*$""").containsMatchIn(before) ||
                    Regex("^(?:小时|钟头|分钟|厘米|毫米|大小|见方)").containsMatchIn(after)) return@replace match.value
                if (!Mass.matches(unit) && names.none {
                        val simple = it.split(Regex("[（(、，,]")).first().trim()
                        simple.isNotEmpty() && (before + after).contains(simple)
                    }) return@replace match.value
            }
            val a = value(first) ?: return@replace match.value
            val b = if (second.isNotEmpty()) value(second) ?: return@replace match.value else null
            format(a * factor) + (if (b != null) range.replace(second, format(b * factor)) else "") + space + unit
        }
    }

    fun recipe(recipe: Recipe, base: Double?, target: Int?): Recipe {
        if (base == null || target == null || base <= 0 || target <= 0) return recipe
        val factor = factor(base, target)
        val names = recipe.ingredients.map { it.name }
        return recipe.copy(
            servings = "$target 人份",
            ingredients = recipe.ingredients.map { it.copy(amount = scale(it.amount, factor, target)) },
            preparation = recipe.preparation.map { scaleText(it, factor, names) },
            steps = recipe.steps.map { it.copy(title = scaleText(it.title, factor, names), text = scaleText(it.text, factor, names), cue = it.cue?.let { cue -> scaleText(cue, factor, names) }) },
            notes = recipe.notes.map { scaleText(it, factor, names) },
        )
    }

    private fun value(token: String): Double? {
        val text = token.replace(" ", "")
        if (text.isEmpty()) return null
        if (text == "半") return 0.5
        if (text.first() in '一'..'龥') {
            var result = 0.0
            var digit = 0.0
            for (char in text) {
                val unit = mapOf('十' to 10.0, '百' to 100.0, '千' to 1000.0)[char]
                if (unit != null) { result += (if (digit == 0.0) 1.0 else digit) * unit; digit = 0.0 }
                else digit = ChineseNumeral[char] ?: return null
            }
            return result + digit
        }
        val parts = text.split('/')
        return if (parts.size == 2) {
            val numerator = parts[0].toDoubleOrNull()
            val denominator = parts[1].toDoubleOrNull()
            if (numerator != null && denominator != null && denominator != 0.0) numerator / denominator else null
        } else {
            text.toDoubleOrNull()
        }
    }

    /** Whole numbers stay whole, common fractions read better than decimals, the rest keep one digit. */
    private fun format(value: Double): String {
        if (value <= 0.0) return "0"
        val rounded = round(value * 100) / 100
        if (abs(rounded - rounded.toLong()) < 0.001) return rounded.toLong().toString()
        Fractions.firstOrNull { (fraction, _) -> abs(rounded - fraction) < 0.005 }?.let { return it.second }
        return String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }
}
