package com.deepcook.app.feature.cooking

/**
 * Remembers model answers per step, so asking the same thing twice while cooking the same dish
 * does not wait for the free model again. Bounded and in memory only: this is a session
 * convenience, not user data, and it never leaves the phone.
 */
class AnswerCache(private val limit: Int = 24) {
    private val entries = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > this@AnswerCache.limit
    }

    /** The step matters: "还要多久" means different things on step 1 and step 6. */
    fun key(stepIndex: Int, question: String): String = "$stepIndex|${normalize(question)}"

    fun get(key: String): String? = entries[key]

    fun put(key: String, answer: String) {
        val trimmed = answer.trim()
        if (trimmed.isEmpty()) return
        entries[key] = trimmed
    }

    fun clear() = entries.clear()

    companion object {
        /** Spacing and punctuation do not change what was asked; case does not either. */
        fun normalize(question: String): String {
            val builder = StringBuilder(question.length)
            for (character in question.lowercase()) {
                if (!character.isWhitespace() && character !in PUNCTUATION) builder.append(character)
            }
            return builder.toString()
        }

        private const val PUNCTUATION = "，。？！、,.?!~～；;：:\"'“”‘’"
    }
}
