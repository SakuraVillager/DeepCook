package com.deepcook.app.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PlaybookEntry(
    val id: String,
    val keywords: List<String>,
    val answer: String,
    val tier: String = "general",
)

@Serializable
data class PlaybookFile(
    val version: Int = 1,
    val entries: List<PlaybookEntry> = emptyList(),
)

/**
 * The fixed answers that must not wait for the model: a burning pan gets its answer from here,
 * offline, in a few milliseconds. Matching is plain keyword containment over a normalised
 * question; the longest keyword wins, so "太咸" beats "咸".
 */
class Playbook(entries: List<PlaybookEntry>) {
    private val table: List<Pair<PlaybookEntry, List<String>>> = entries.map { entry ->
        entry to entry.keywords.map(::normalize).filter { it.isNotEmpty() }
    }

    fun match(question: String): PlaybookEntry? {
        val text = normalize(question)
        if (text.isEmpty()) return null
        var best: PlaybookEntry? = null
        var bestLength = 0
        for ((entry, keywords) in table) {
            for (keyword in keywords) {
                if (keyword.length > bestLength && text.contains(keyword)) {
                    best = entry
                    bestLength = keyword.length
                }
            }
        }
        return best
    }

    companion object {
        private val IGNORED = "，。？！、,.?!;；:：\"'“”‘’（）()【】[]…—-_ \t\n".toSet()

        fun normalize(text: String): String = text.lowercase().filterNot { it in IGNORED }
    }
}

/** Loads `assets/ai_playbook.json` once, off the main thread. */
class AssetPlaybook(private val context: Context) {
    @Volatile
    private var cached: Playbook? = null

    suspend fun get(): Playbook = cached ?: withContext(Dispatchers.IO) {
        val text = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val file = json.decodeFromString<PlaybookFile>(text)
        Playbook(file.entries).also { cached = it }
    }

    companion object {
        const val ASSET = "ai_playbook.json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
