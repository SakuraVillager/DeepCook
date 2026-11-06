package com.deepcook.app.data.ai

import kotlinx.coroutines.flow.Flow

/** One turn sent to the model. Roles are the OpenAI-compatible `system` / `user` / `assistant`. */
data class AiMessage(val role: String, val content: String) {
    companion object {
        fun system(text: String) = AiMessage("system", text)
        fun user(text: String) = AiMessage("user", text)
        fun assistant(text: String) = AiMessage("assistant", text)
    }
}

/**
 * What comes back from the assistant. [Delta] is a fragment of the answer as it streams in;
 * the three failure kinds exist because the free model can be rate limited or offline, and the
 * cooking screen answers both with the current step instead of an error dialog.
 */
sealed interface AiEvent {
    data class Delta(val text: String) : AiEvent
    data object Done : AiEvent
    data object Busy : AiEvent
    data object Offline : AiEvent

    /** [status] is the HTTP code when the server answered, null when the connection failed. */
    data class Failed(val status: Int? = null) : AiEvent
}

/** One finished question/answer pair, kept only for this cooking session. */
data class AiExchange(val question: String, val answer: String)

interface AiGateway {
    /**
     * Streams one answer. [model] overrides the default, which the cooking screen uses to fall
     * back to a second free model when the primary one is rate limited.
     */
    fun stream(messages: List<AiMessage>, model: String? = null): Flow<AiEvent>

    /** One non-streaming answer, used for field enrichment; null when the call failed. */
    suspend fun complete(messages: List<AiMessage>): String?
}
