package com.deepcook.app.data.ai

/**
 * Turns "有点咸，下次少放点" into a one-line note the cooking screen can speak next time.
 * Falls back to the user's own words when the model is unavailable — a rough note is better
 * than a lost one.
 */
class NoteSuggester(private val gateway: AiGateway) {
    suspend fun rewrite(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val messages = listOf(AiMessage.system(SYSTEM), AiMessage.user(text))
        return clean(gateway.complete(messages) ?: return null)
    }

    companion object {
        private const val SYSTEM =
            "把用户对一道菜的反馈压成一句 20 字以内的做菜备注。只输出备注本身，不要引号、不要解释。" +
                "例：有点咸 → 下次少放盐。"

        fun clean(answer: String): String? {
            val first = answer.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
            val stripped = first.trim().trim('"', '\'', '“', '”', '「', '」', '。', ' ')
            return stripped.takeIf { it.isNotEmpty() }?.take(20)
        }
    }
}
