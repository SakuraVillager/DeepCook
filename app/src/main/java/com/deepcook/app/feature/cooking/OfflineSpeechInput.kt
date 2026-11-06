package com.deepcook.app.feature.cooking

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Speech-to-text that runs entirely on the phone, with two bundled sherpa-onnx models doing
 * different jobs:
 *
 * - a small streaming zipformer gives **live words** while the cook is talking and, more
 *   importantly, tells us when the sentence has ended (its endpoint detector looks at the audio
 *   itself, so a quiet microphone no longer cuts people off after one word);
 * - a Paraformer model re-reads the recorded clip once the sentence ends and produces the
 *   **final text**, because the 14M streaming model confuses homophones ("盐" → "严", "炖" → "顿").
 *
 * Nothing leaves the device and no system speech service is involved; the platform recogniser on
 * some ROMs accepts a request and never calls back at all.
 */
class OfflineSpeechInput(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private var job: Job? = null

    private var streaming: OnlineRecognizer? = null
    private var offline: OfflineRecognizer? = null

    @Volatile
    private var recorder: AudioRecord? = null

    @Volatile
    private var active = false

    @Volatile
    private var disposed = false

    @Volatile
    var lastError: String? = null
        private set

    fun isActive(): Boolean = active

    /** Loads both models ahead of the first tap, so the microphone starts without a pause. */
    fun prepare() {
        if (streaming == null || offline == null) {
            scope.launch {
                streamingOrNull()
                offlineOrNull()
            }
        }
    }

    /**
     * Starts one utterance. Callbacks arrive on the main thread. [onFinal] carries the recognised
     * text when the speaker stops or [stop] is called; [onError] explains why nothing was heard.
     */
    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()
        active = true
        job = scope.launch { loop(onPartial, onFinal, onError) }
    }

    /** Ends the utterance; whatever has been heard so far is delivered as the final text. */
    fun stop() {
        active = false
        runCatching { recorder?.stop() }
        job?.cancel()
        job = null
    }

    fun release() {
        disposed = true
        stop()
        scope.cancel()
        runCatching { streaming?.release() }
        runCatching { offline?.release() }
        streaming = null
        offline = null
    }

    private fun loop(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val engine = streamingOrNull()
        if (engine == null) {
            active = false
            post { onError("端侧识别模型没能加载") }
            return
        }
        val stream = runCatching { engine.createStream("") }.getOrNull()
        if (stream == null) {
            active = false
            post { onError("端侧识别启动失败") }
            return
        }
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val size = maxOf(minimum, SAMPLE_RATE / 10 * 2 * 2)
        val record = runCatching { AudioRecord(SOURCE, SAMPLE_RATE, CHANNEL, ENCODING, size) }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            active = false
            runCatching { record?.release() }
            recorder = null
            runCatching { stream.release() }
            post { onError("麦克风打不开，可能被别的应用占用") }
            return
        }
        recorder = record
        val chunks = ArrayList<FloatArray>()
        var preview = ""
        var peak = 0f
        try {
            record.startRecording()
            val buffer = ShortArray(1600)
            val startedAt = System.currentTimeMillis()
            var lastPushAt = 0L
            while (active) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val samples = FloatArray(read) { buffer[it] / 32768f }
                var sum = 0f
                for (sample in samples) sum += sample * sample
                val rms = sqrt(sum / read)
                if (rms > peak) peak = rms
                chunks.add(samples)
                val now = System.currentTimeMillis()
                stream.acceptWaveform(samples, SAMPLE_RATE)
                while (engine.isReady(stream)) engine.decode(stream)
                val partial = engine.getResult(stream).text
                if (partial != preview) {
                    preview = partial
                    if (preview.isNotEmpty() && now - lastPushAt > 120) {
                        lastPushAt = now
                        post { onPartial(preview) }
                    }
                }
                when {
                    // Nothing at all has been heard: say so instead of waiting out the maximum.
                    preview.isEmpty() && now - startedAt > 8_000 && peak < AUDIBLE_LEVEL -> {
                        post { onError("麦克风里没有声音进来（可能被系统静音或占用）") }
                        break
                    }
                    // The streaming model's endpoint rule decides "said it": a real pause in the
                    // audio, not a dip in the microphone level.
                    preview.isNotEmpty() && engine.isEndpoint(stream) -> break
                    now - startedAt > MAX_UTTERANCE_MS -> break
                    else -> Unit
                }
            }
            // Decode what is still buffered so the preview is complete before the second pass.
            runCatching { stream.inputFinished() }
            while (engine.isReady(stream)) engine.decode(stream)
            preview = engine.getResult(stream).text.ifBlank { preview }
        } catch (error: Throwable) {
            post { onError(error.message ?: "识别出错") }
        } finally {
            active = false
            runCatching { record.stop() }
            runCatching { record.release() }
            recorder = null
            runCatching { stream.release() }
        }
        if (disposed) return
        // Second pass: the same clip through the Paraformer model, which does not confuse
        // homophones. If it is unavailable the streaming text still stands.
        val refined = transcribe(join(chunks))
        val result = refined.trim().ifEmpty { preview.trim() }
        if (result.isEmpty()) {
            post { onError("没听到内容，再说一次") }
        } else {
            post { onFinal(result) }
        }
    }

    private fun transcribe(samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        val engine = offlineOrNull() ?: return ""
        val stream = runCatching { engine.createStream() }.getOrNull() ?: return ""
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            engine.decode(stream)
            engine.getResult(stream).text
        } catch (_: Throwable) {
            ""
        } finally {
            runCatching { stream.release() }
        }
    }

    private fun join(chunks: List<FloatArray>): FloatArray {
        val total = chunks.sumOf { it.size }
        val audio = FloatArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(audio, offset)
            offset += chunk.size
        }
        return audio
    }

    private fun streamingOrNull(): OnlineRecognizer? {
        streaming?.let { return it }
        // Zipformer releases differ in how the model type must be declared; try the plain config
        // first, then the explicit names, so one wrong guess does not disable voice entirely.
        for (type in STREAMING_MODEL_TYPES) {
            val created = runCatching { OnlineRecognizer(context.assets, streamingConfig(type)) }
                .onFailure { lastError = it.message ?: it.javaClass.simpleName }
                .getOrNull()
            if (created != null) {
                streaming = created
                lastError = null
                return created
            }
        }
        return null
    }

    private fun offlineOrNull(): OfflineRecognizer? {
        offline?.let { return it }
        val created = runCatching {
            OfflineRecognizer(
                context.assets,
                OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        paraformer = OfflineParaformerModelConfig(model = PARAFORMER),
                        tokens = PARAFORMER_TOKENS,
                        numThreads = 2,
                    ),
                    decodingMethod = "greedy_search",
                ),
            )
        }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
        offline = created
        return created
    }

    private fun streamingConfig(modelType: String): OnlineRecognizerConfig = OnlineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = ENCODER,
                decoder = DECODER,
                joiner = JOINER,
            ),
            tokens = TOKENS,
            numThreads = 2,
            modelType = modelType,
        ),
        // Endpoint detection based on the recogniser's own silence model is what decides when the
        // cook has finished a sentence; rule 2 only fires once speech has been heard.
        endpointConfig = EndpointConfig(
            rule1 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 2.4f, minUtteranceLength = 0f),
            rule2 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 1.5f, minUtteranceLength = 0f),
            rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0f, minUtteranceLength = 25f),
        ),
        enableEndpoint = true,
    )

    private fun post(block: () -> Unit) {
        if (disposed) return
        main.post { if (!disposed) block() }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
        const val ENCODER = "asr/encoder.int8.onnx"
        const val DECODER = "asr/decoder.int8.onnx"
        const val JOINER = "asr/joiner.int8.onnx"
        const val TOKENS = "asr/tokens.txt"
        const val PARAFORMER = "asr-paraformer/model.int8.onnx"
        const val PARAFORMER_TOKENS = "asr-paraformer/tokens.txt"

        /** Below this peak after eight seconds there was no usable audio at all. */
        const val AUDIBLE_LEVEL = 0.004f
        const val MAX_UTTERANCE_MS = 25_000L
        val STREAMING_MODEL_TYPES = listOf("", "zipformer2", "zipformer")
    }
}
