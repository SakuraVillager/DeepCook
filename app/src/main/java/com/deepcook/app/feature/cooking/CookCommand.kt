package com.deepcook.app.feature.cooking

/**
 * Recognises the one command the cooking screen can act on without the model: "计时十分钟".
 *
 * A bare duration only counts when it is the whole utterance, so "炖十分钟够吗" or "要炖多久"
 * stay questions instead of silently starting a timer.
 */
object CookCommand {
    private val CommandWords = listOf("计时", "定时", "倒计时", "提醒我", "闹钟", "设个", "定个")
    private val Number = "(\\d{1,3}|[一二两三四五六七八九十]{1,3})"
    private val Unit = "(分钟|分|个?小时|钟头)"
    private val Duration = Regex("$Number\\s*$Unit")
    private val BareDuration = Regex("^$Number\\s*$Unit$")
    private val HalfHour = Regex("半\\s*个?\\s*(小时|钟头)")
    /** The recogniser now returns punctuated text ("十分钟。"), so clause marks are not part of it. */
    private const val Punctuation = "。，、；：！？…,.!?;: "

    fun timerMinutes(text: String): Int? {
        val trimmed = text.trim().trim { it in Punctuation }
        if (trimmed.isEmpty()) return null
        val command = CommandWords.any { trimmed.contains(it) }
        if (!command && !BareDuration.matches(trimmed) && !HalfHour.matches(trimmed)) return null
        if (HalfHour.containsMatchIn(trimmed)) return 30
        val match = Duration.find(trimmed) ?: return null
        val amount = parseNumber(match.groupValues[1]) ?: return null
        val unit = match.groupValues[2]
        val minutes = if (unit.contains("小时") || unit.contains("钟头")) amount * 60 else amount
        return minutes.takeIf { it in 1..180 }
    }

    private fun parseNumber(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        val digit = mapOf(
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5,
            '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        if (raw.length == 1) return if (raw[0] == '十') 10 else digit[raw[0]]
        if (raw[0] == '十' && raw.length == 2) return 10 + (digit[raw[1]] ?: return null)
        if (raw.length == 2 && raw[1] == '十') return (digit[raw[0]] ?: return null) * 10
        if (raw.length == 3 && raw[1] == '十') {
            val tens = digit[raw[0]] ?: return null
            val ones = digit[raw[2]] ?: return null
            return tens * 10 + ones
        }
        return null
    }
}
