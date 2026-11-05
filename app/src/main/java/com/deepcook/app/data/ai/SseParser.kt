package com.deepcook.app.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads one Server-Sent-Events line from the chat completions stream.
 *
 * The provider sends a `data:` line per token, plus role-only and finish chunks that carry no
 * text; this function returns null for everything that is not a content fragment.
 */
object SseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun delta(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return null
        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        return runCatching {
            json.parseToJsonElement(payload).jsonObject["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("delta")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.content
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}
