package com.deepcook.app.data.ai

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Serving maths for the recipe detail page.
 *
 * Scaling is done here, not by the model: it is instant, it works offline, and it cannot invent a
 * quantity — the printed numbers are multiplied and nothing else changes. Amounts that are already
 * relative (`* 份数`), non-numeric (`适量`, `半个`) or carry a bracketed equivalent (`1 个（约 80g）`)
 * are left exactly as written, because scaling half of such a line would be wrong.
 */
object ServingScale {
    /** Markers of an amount that must not be multiplied. */
    private val Unscalable = listOf(
        "份数", "斤数", "*", "×", "各", "其中", "按口味", "适量", "少许", "少量", "若干", "体积", "%",
    )

    private val ChineseNumeral = mapOf(
        '一' to 1.0, '两' to 2.0, '二' to 2.0, '三' to 3.0, '四' to 4.0, '五' to 5.0,
        '六' to 6.0, '七' to 7.0, '八' to 8.0, '九' to 9.0, '十' to 10.0,
    )

    private val Number = Regex("""\d+(?:\.\d+)?""")

    /** Prefix, first number, an optional range (separator and end), then the unit and any note. */
    private val Leading = Regex(
        """^(约\s*|大约\s*)?(\d+(?:\.\d+)?(?:\s*/\s*\d+)?)(?:(\s*[~～\-—–]\s*)(\d+(?:\.\d+)?))?(\s*.*)$""",
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
        val text = label(servings)
        Number.find(text)?.value?.toDoubleOrNull()?.let { count -> return count.takeIf { it in 0.5..50.0 } }
        return text.firstNotNullOfOrNull { ChineseNumeral[it] }
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
    fun scale(amount: String, factor: Double): String {
        val text = amount.trim()
        if (text.isEmpty() || factor <= 0.0 || abs(factor - 1.0) < 0.0001) return amount
        if (Unscalable.any { it in text }) return amount
        if (hasBracketedNumber(text)) return amount
        val match = Leading.matchEntire(text) ?: return amount
        val prefix = match.groupValues[1]
        val first = value(match.groupValues[2]) ?: return amount
        val range = match.groupValues[3]
        val rest = match.groupValues[5]
        return buildString {
            append(prefix)
            append(format(first * factor))
            if (range.isNotEmpty()) {
                append(range)
                append(format((value(match.groupValues[4]) ?: first) * factor))
            }
            append(rest)
        }
    }

    /** "1 个（约 80g）" carries its own equivalent; scaling only the count would contradict it. */
    private fun hasBracketedNumber(text: String): Boolean {
        var depth = 0
        var digit = false
        for (character in text) {
            when {
                character == '（' || character == '(' -> {
                    depth++
                    digit = false
                }

                character == '）' || character == ')' -> {
                    if (depth > 0 && digit) return true
                    if (depth > 0) depth--
                }

                character.isDigit() && depth > 0 -> digit = true
            }
        }
        return false
    }

    private fun value(token: String): Double? {
        val text = token.replace(" ", "")
        if (text.isEmpty()) return null
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
        Fractions.firstOrNull { (fraction, _) -> abs(rounded - fraction) < 0.02 }?.let { return it.second }
        return String.format(Locale.US, "%.1f", rounded)
    }
}
