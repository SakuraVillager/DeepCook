package com.deepcook.app.feature.cooking

/**
 * "记一笔：少放盐" → "少放盐". Only an explicit note command is treated as a note, so ordinary
 * praise or complaints stay conversation instead of silently rewriting the dish memory.
 */
object NoteCommand {
    // Longest first: "帮我记住" must win over "帮我记", otherwise "住" leaks into the note.
    private val Prefixes = listOf("帮我记住", "帮我记", "记下来", "记一下", "记一笔", "记住", "备注", "记下")

    // The recogniser punctuates now ("少放盐。"), and the mark is not part of what the cook said.
    private const val TrailingMarks = "。，、；：！？…,.!?;: "

    fun extract(text: String): String? {
        val trimmed = text.trim()
        val prefix = Prefixes.firstOrNull { trimmed.startsWith(it) } ?: return null
        val rest = trimmed.removePrefix(prefix)
            .trim()
            .removePrefix("一下")
            .trimStart('：', ':', '，', ',', '、', ' ')
            .trim()
            .trimEnd { it in TrailingMarks }
        if (rest.isEmpty()) return null
        return rest.take(20)
    }
}
