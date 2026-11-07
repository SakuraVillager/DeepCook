package com.deepcook.app.data.ai

import com.deepcook.app.core.model.Recipe

/**
 * Shapes what the detail page shows.
 *
 * The free model sometimes ignores the two-sentence rule, so the answer is cut here rather than
 * trusted. The two local texts are what the page falls back to when the model is queued, offline
 * or out of quota — the recipe's own words, never an invented one.
 */
object DishAnswer {
    private const val SENTENCE_END = "。！？!?；;\n"

    /** Keeps at most two sentences; the rest is dropped rather than shown as a wall of text. */
    fun twoSentences(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        var seen = 0
        trimmed.forEachIndexed { index, character ->
            if (character in SENTENCE_END) {
                seen++
                if (seen == 2) return trimmed.substring(0, index + 1).trim()
            }
        }
        return trimmed
    }

    /** The recipe's own steps and warnings, used when no answer arrived in time. */
    fun recipeFallback(recipe: Recipe): String = buildString {
        val titles = recipe.steps.take(5).joinToString("、") { it.title }
        if (titles.isNotEmpty()) append("先按菜谱来：$titles。")
        recipe.noteItems.take(1).firstOrNull()?.let { note -> append("注意：$note") }
        if (isBlank()) append("这道菜暂时没有更多说明，按材料表里的用量做就行。")
    }

    /** What the recipe prints for [name], plus the one safe action when it is missing. */
    fun missingIngredientHint(name: String, amount: String): String {
        val printed = amount.trim()
        return if (printed.isEmpty() || EnrichmentRules.isPlaceholder(printed)) {
            "菜谱里「$name」只写了适量。没有的话可以先不放，出锅前尝一下再补盐。"
        } else {
            "菜谱里「$name」写的是 $printed。没有的话可以先省略，出锅前尝一下再补盐。"
        }
    }
}
