package com.deepcook.app.core.model

import java.util.Locale

data class StepDuration(val text: String, val minimumSeconds: Long, val maximumSeconds: Long) {
    val isRange: Boolean get() = minimumSeconds != maximumSeconds
}

/** Recognise explicit durations only; never infer time from temperature, size or doneness. */
object StepDurations {
    private const val NUMBER = "(?:[0-9]+/[0-9]+|[0-9]+(?:\\.[0-9]+)?|[零一二两三四五六七八九十百半]+)"
    private const val RANGE = "$NUMBER(?:\\s*[-~～–—至到]\\s*$NUMBER)?"
    private const val UNIT =
        "(?:个?小时|钟头|分钟|秒钟|秒|分(?!熟|饱)|(?:hours?|hrs?|h|minutes?|mins?|seconds?|secs?|s)(?![A-Za-z]))"
    private val part = Regex("($RANGE)\\s*($UNIT)", RegexOption.IGNORE_CASE)
    private val phrase = Regex("$RANGE\\s*$UNIT(?:\\s*$RANGE\\s*$UNIT)*(?:半)?", RegexOption.IGNORE_CASE)
    private val digits = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7,
        '八' to 8, '九' to 9,
    )

    fun parse(text: String): List<StepDuration> {
        val parsed = phrase.findAll(text).mapNotNull { match ->
            var minimum = 0.0
            var maximum = 0.0
            var lastUnit = 0.0
            var valid = true
            part.findAll(match.value).forEach { p ->
                val unit = p.groupValues[2].lowercase(Locale.ROOT)
                val scale = when {
                    unit.contains("小时") || unit == "钟头" || unit.startsWith("h") -> 3600.0
                    unit.contains("分") || unit.startsWith("min") -> 60.0
                    else -> 1.0
                }
                lastUnit = scale
                val values = p.groupValues[1].split(Regex("\\s*[-~～–—至到]\\s*"))
                val bounds = if (values.size == 1 && values[0].length == 2 && values[0].all { it in digits } &&
                    values[0].none { it == '零' }
                ) {
                    values[0].map { digits.getValue(it).toDouble() }
                } else {
                    values.map {
                        number(it) ?: run {
                            valid = false
                            0.0
                        }
                    }
                }
                if (bounds.first() > bounds.last()) valid = false
                minimum += bounds.first() * scale
                maximum += bounds.last() * scale
            }
            if (match.value.endsWith("半")) {
                minimum += lastUnit / 2
                maximum += lastUnit / 2
            }
            if (valid && minimum >= 1 && maximum <= 7 * 86400) {
                match.range to StepDuration(match.value, minimum.toLong(), maximum.toLong())
            } else {
                null
            }
        }.toList()
        val merged = mutableListOf<Pair<IntRange, StepDuration>>()
        parsed.forEach { current ->
            val previous = merged.lastOrNull()
            val separator = previous?.let { text.substring(it.first.last + 1, current.first.first).trim() }
            if (previous != null && separator in setOf("至", "到", "-", "~", "～", "–", "—")) {
                merged.removeAt(merged.lastIndex)
                if (previous.second.minimumSeconds <= current.second.maximumSeconds) {
                    val range = previous.first.first..current.first.last
                    merged.add(
                        range to
                            StepDuration(
                                text.substring(range),
                                previous.second.minimumSeconds,
                                current.second.maximumSeconds,
                            ),
                    )
                }
            } else {
                merged.add(current)
            }
        }
        // Apply the threshold after merging ranges and compound phrases; a 5–15 s range
        // still offers the useful 15 s upper bound, while a 1–9 s range is omitted.
        return merged.map { it.second }.filter { it.maximumSeconds >= 10 }
    }

    private fun number(text: String): Double? {
        if ('/' in text) {
            val parts = text.split('/')
            val denominator = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
            return if (denominator > 0) parts[0].toDouble() / denominator else null
        }
        text.toDoubleOrNull()?.let { return it }
        if (text == "半") return .5
        var sum = 0
        var pending = 0
        text.forEach { char ->
            when (char) {
                '十' -> {
                    sum += pending.coerceAtLeast(1) * 10
                    pending = 0
                }
                '百' -> {
                    sum += pending.coerceAtLeast(1) * 100
                    pending = 0
                }
                else -> pending = digits[char] ?: return null
            }
        }
        return (sum + pending).toDouble()
    }
}

fun countdownLabel(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) + 999) / 1000
    return if (seconds >= 3600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }
}
