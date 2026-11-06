package com.deepcook.app.feature.cooking

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listens for "小厨小厨" while the cooking page is in front, entirely on the phone: the keyword
 * spotter model ships in assets and no audio leaves the device.
 *
 * One detection stops the loop (the microphone is handed to speech recognition) and invokes
 * [onWake] on a background thread; the caller must hop to the main thread.
 */
class WakeWordEngine(private val context: Context, private val onWake: () -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /**
     * The live recorder, kept outside the loop so [stop] can end a blocking read immediately.
     * Leaving it to coroutine cancellation alone can keep the microphone open for a moment,
     * which is enough for the speech recognizer started right after to get silence.
     */
    @Volatile
    private var recorder: AudioRecord? = null

    @Volatile
    private var running = false

    /** Last failure message, or null when the last start got as far as recording. */
    @Volatile
    var lastError: String? = null
        private set

    fun isRunning(): Boolean = running

    fun start() {
        if (running) return
        running = true
        lastError = null
        job = scope.launch { loop() }
    }

    fun stop() {
        running = false
        runCatching { recorder?.stop() }
        job?.cancel()
        job = null
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun loop() {
        var spotter: KeywordSpotter? = null
        try {
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = ENCODER,
                        decoder = DECODER,
                        joiner = JOINER,
                    ),
                    tokens = TOKENS,
                    numThreads = 2,
                    modelType = "zipformer2",
                ),
                keywordsFile = KEYWORDS,
                keywordsScore = 1.5f,
                keywordsThreshold = 0.25f,
            )
            val kws = KeywordSpotter(context.assets, config)
            spotter = kws
            val stream = kws.createStream()

            val minimum = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val recorderSize = maxOf(minimum, SAMPLE_RATE / 10 * 2 * 2)
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recorderSize,
            )
            recorder = record
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                lastError = "麦克风被占用"
                return
            }
            record.startRecording()
            val buffer = ShortArray(1600)
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val samples = FloatArray(read) { buffer[it] / 32768.0f }
                stream.acceptWaveform(samples, SAMPLE_RATE)
                while (kws.isReady(stream)) kws.decode(stream)
                // This build exposes the detection as a result whose keyword is empty until the
                // phrase is heard (there is no isDetected() on the Java wrapper).
                if (kws.getResult(stream).keyword.isNotBlank()) {
                    kws.reset(stream)
                    running = false
                    onWake()
                    break
                }
            }
        } catch (error: Throwable) {
            // A missing native library or a model that cannot load must never crash cooking.
            lastError = error.message ?: error.javaClass.simpleName
        } finally {
            running = false
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            recorder = null
            runCatching { spotter?.release() }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val ENCODER = "kws/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        private const val DECODER = "kws/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        private const val JOINER = "kws/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        private const val TOKENS = "kws/tokens.txt"
        private const val KEYWORDS = "kws/keywords.txt"
    }
}
