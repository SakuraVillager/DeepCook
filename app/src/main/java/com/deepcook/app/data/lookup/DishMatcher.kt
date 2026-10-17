package com.deepcook.app.data.lookup

import kotlinx.serialization.Serializable

/** One dish in the bundled index: the human name, where it lives, and which folder it came from. */
@Serializable
data class DishEntry(val name: String, val path: String, val group: String)

/** `assets/howtocook_index.json`, produced by `tools/build_howtocook_index.py`. */
@Serializable
data class CookIndex(
    val source: String = "",
    val license: String = "",
    /** Pinned commit, so a lookup always shows the text this project reviewed. */
    val ref: String = "",
    val commitDate: String = "",
    val dishes: List<DishEntry> = emptyList(),
)

/**
 * Picks the catalogue dish a typed name refers to.
 *
 * Chinese dish names are short and share ingredients, so a character-level score is too loose
 * (it rates 韭黄炒鸡蛋 and 西红柿炒鸡蛋 as similar). Matching therefore runs in three passes —
 * exact, containment, then character-bigram overlap — and only accepts the fuzzy pass above a
 * threshold that keeps clearly different dishes apart. A wrong match is not fatal: the editor
 * prefills and tells the user which dish it matched, and every field stays editable.
 */
object DishMatcher {
    /** Common ingredient spellings, folded to one token before comparing. */
    private val SYNONYMS = listOf(
        "西红柿" to "番茄",
        "马铃薯" to "土豆",
        "洋芋" to "土豆",
        "番薯" to "红薯",
        "地瓜" to "红薯",
        "青椒" to "柿子椒",
        "圆白菜" to "卷心菜",
        "包菜" to "卷心菜",
        "鸡子" to "鸡蛋",
    )

    private const val MIN_FUZZY = 0.55

    fun match(query: String, entries: List<DishEntry>): DishEntry? {
        val wanted = canonical(query)
        if (wanted.isEmpty()) return null

        entries.firstOrNull { canonical(it.name) == wanted }?.let { return it }

        val contained = entries
            .filter { entry ->
                val name = canonical(entry.name)
                (name.contains(wanted) || wanted.contains(name)) &&
                    minOf(name.length, wanted.length) >= 2
            }
            // Closest in length is the most specific candidate, e.g. prefer 冬瓜排骨汤 over 排骨.
            .minByOrNull { kotlin.math.abs(canonical(it.name).length - wanted.length) }
        if (contained != null) return contained

        return entries
            .map { it to overlap(wanted, canonical(it.name)) }
            .filter { (_, score) -> score >= MIN_FUZZY }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    /** Lower-cases, drops spaces and punctuation, then folds known synonyms to one token. */
    fun canonical(raw: String): String {
        var text = raw.trim().lowercase()
        text = text.filter { it.isLetterOrDigit() }
        SYNONYMS.forEach { (from, to) -> text = text.replace(from, to) }
        return text
    }

    /** Dice coefficient over character bigrams; 1.0 means identical. */
    fun overlap(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.length < 2 || right.length < 2) return 0.0
        val a = bigrams(left)
        val b = bigrams(right)
        val shared = a.keys.sumOf { key -> minOf(a.getValue(key), b[key] ?: 0) }
        return 2.0 * shared / (left.length - 1 + right.length - 1)
    }

    private fun bigrams(text: String): Map<String, Int> = buildMap {
        for (index in 0 until text.length - 1) {
            val gram = text.substring(index, index + 2)
            put(gram, (this[gram] ?: 0) + 1)
        }
    }
}
