package com.deepcook.app.data.ai

import com.deepcook.app.data.lookup.NetworkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The Zhipu chat-completions gateway (OpenAI-compatible), streaming over plain SSE.
 *
 * Thinking is explicitly disabled: `glm-4.7-flash` reasons by default, and with a short answer
 * budget those reasoning tokens eat the whole reply (observed: empty content, 117 reasoning
 * tokens). Offline and rate limiting are reported as events, never as thrown errors.
 */
class ZhipuGateway(
    private val apiKey: String,
    private val network: NetworkStatus,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : AiGateway {
    override fun stream(messages: List<AiMessage>): Flow<AiEvent> = flow {
        if (apiKey.isBlank()) {
            emit(AiEvent.Failed)
            return@flow
        }
        if (!network.isOnline()) {
            emit(AiEvent.Offline)
            return@flow
        }
        val body = payload(messages, stream = true).encodeToByteArray()
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        // Closing the socket on cancellation keeps "user asked something else" instant.
        val disconnectOnCancel = currentCoroutineContext().job.invokeOnCompletion { connection.disconnect() }
        try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            when (status) {
                200 -> Unit
                429 -> {
                    emit(AiEvent.Busy)
                    return@flow
                }
                else -> {
                    emit(AiEvent.Failed)
                    return@flow
                }
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    val delta = SseParser.delta(line) ?: continue
                    emit(AiEvent.Delta(delta))
                }
            }
            emit(AiEvent.Done)
        } catch (_: IOException) {
            emit(AiEvent.Failed)
        } finally {
            disconnectOnCancel.dispose()
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(messages: List<AiMessage>): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || !network.isOnline()) return@withContext null
        val body = payload(messages, stream = false).encodeToByteArray()
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != 200) return@withContext null
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            runCatching {
                json.parseToJsonElement(text).jsonObject["choices"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("message")
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull()?.takeIf { it.isNotBlank() }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun payload(messages: List<AiMessage>, stream: Boolean): String = buildJsonObject {
        put("model", MODEL)
        put("stream", stream)
        put("temperature", 0.3)
        put("max_tokens", if (stream) 120 else 400)
        putJsonObject("thinking") { put("type", "disabled") }
        putJsonArray("messages") {
            messages.forEach { message ->
                addJsonObject {
                    put("role", message.role)
                    put("content", message.content)
                }
            }
        }
    }.toString()

    companion object {
        const val MODEL = "glm-4.7-flash"
        const val DEFAULT_ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
