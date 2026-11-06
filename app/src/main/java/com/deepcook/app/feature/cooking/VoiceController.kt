package com.deepcook.app.feature.cooking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Wraps the system speech recognizer. No audio ever leaves the app through this class beyond
 * what the platform's own recognition service does; the app itself sends only the final text.
 *
 * Every start gets a new generation number. Callbacks from a recognizer that was already
 * replaced or destroyed are ignored, because destroying one can still deliver [onError] on the
 * old listener — which previously reset the screen while the new utterance was starting.
 */
class VoiceInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0

    @Volatile
    private var active = false

    fun isActive(): Boolean = active

    /**
     * Starts one utterance. [onError] receives the platform error code along with a readable
     * message, so the screen can fall back to the system speech dialog for real failures but
     * simply ask again after "didn't catch that".
     */
    fun start(
        onText: (String) -> Unit,
        onError: (Int, String) -> Unit,
        onPartial: (String) -> Unit = {},
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError(ERROR_NOT_AVAILABLE, "这台手机没有语音识别服务")
            return
        }
        release()
        val token = ++generation
        val speech = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (speech == null) {
            onError(SpeechRecognizer.ERROR_CLIENT, "语音识别没能启动")
            return
        }
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            private fun stale(): Boolean = token != generation

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                if (stale()) return
                active = false
                onError(error, messageFor(error))
            }

            override fun onResults(results: Bundle?) {
                if (stale()) return
                active = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (text.isNotEmpty()) onText(text) else onError(SpeechRecognizer.ERROR_NO_MATCH, "没听清，再说一遍")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (stale()) return
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (text.isNotEmpty()) onPartial(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            // Some third-party recognition services want the caller named explicitly; without it
            // they accept the request and then stay silent instead of reporting an error.
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // End the utterance sooner: a short kitchen command should not wait for a long pause.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 400L)
        }
        val started = runCatching { speech.startListening(intent) }.isSuccess
        active = started
        if (!started) onError(SpeechRecognizer.ERROR_CLIENT, "麦克风没能启动")
    }

    fun stop() {
        generation++
        active = false
        release()
    }

    /** Detaches the listener first, so destroying a recognizer cannot call back into the screen. */
    private fun release() {
        val speech = recognizer ?: return
        recognizer = null
        runCatching { speech.setRecognitionListener(null) }
        runCatching { speech.stopListening() }
        runCatching { speech.cancel() }
        runCatching { speech.destroy() }
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "麦克风不可用或正被占用"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别需要联网"
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听清，再说一遍"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别正忙，稍后再试"
        SpeechRecognizer.ERROR_SERVER -> "语音服务出错"
        else -> "语音识别没响应"
    }

    companion object {
        /** Not a platform code: the device simply has no recognition service installed. */
        const val ERROR_NOT_AVAILABLE = -100
    }
}

/**
 * System text-to-speech at 1.3x. Utterances queue, so a streamed answer can be handed over one
 * sentence at a time and starts speaking before the model has finished.
 *
 * [voiceName] picks one of the voices the system engine already has installed; null keeps the
 * engine's default for Chinese. Voice data itself is downloaded by the engine, never shipped in
 * the APK.
 */
class TtsSpeaker(context: Context, private val voiceName: String? = null) {
    private var engine: TextToSpeech? = null

    @Volatile
    private var ready = false
    private val pending = ArrayDeque<String>()

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                runCatching { engine?.language = Locale.SIMPLIFIED_CHINESE }
                voiceName?.let { name ->
                    runCatching {
                        engine?.voices?.firstOrNull { it.name == name }?.let { chosen -> engine?.voice = chosen }
                    }
                }
                runCatching { engine?.setSpeechRate(1.3f) }
                val queued = synchronized(pending) {
                    pending.toList().also { pending.clear() }
                }
                queued.forEach(::speakNow)
            }
        }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!ready) {
            synchronized(pending) { pending.addLast(clean) }
            return
        }
        speakNow(clean)
    }

    private fun speakNow(text: String) {
        runCatching { engine?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString()) }
    }

    fun stop() {
        synchronized(pending) { pending.clear() }
        runCatching { engine?.stop() }
    }

    fun destroy() {
        synchronized(pending) { pending.clear() }
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
    }
}

/** One selectable system voice, named for the user rather than for the engine. */
data class TtsVoiceOption(val name: String, val label: String)

/**
 * Lists the Chinese voices the current system engine already has.
 *
 * Voice data is owned by the TTS engine (the same place Android's own settings download it), so
 * nothing is bundled in the APK; the two intents below open the engine's download/settings pages.
 */
suspend fun installedChineseVoices(context: Context): List<TtsVoiceOption> =
    suspendCancellableCoroutine { continuation ->
        var engine: TextToSpeech? = null
        var finished = false
        fun finish(options: List<TtsVoiceOption>) {
            if (finished) return
            finished = true
            runCatching { engine?.shutdown() }
            continuation.resume(options)
        }
        engine = TextToSpeech(context.applicationContext) { status ->
            val options = if (status == TextToSpeech.SUCCESS) {
                runCatching {
                    engine?.voices.orEmpty()
                        .filter { it.locale.language == "zh" }
                        .map { voice ->
                            val connection = if (voice.isNetworkConnectionRequired) "（联网）" else "（离线）"
                            TtsVoiceOption(voice.name, voice.locale.displayName + connection)
                        }
                        .distinctBy { it.name }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            finish(options)
        }
        continuation.invokeOnCancellation { finish(emptyList()) }
    }

/** Opens the engine's own "download voice data" screen; false when no engine handles it. */
fun openVoiceDownload(context: Context): Boolean = runCatching {
    context.startActivity(
        Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}.isSuccess

/** Opens the system text-to-speech settings, where the engine and voice can also be changed. */
fun openSystemTtsSettings(context: Context): Boolean = runCatching {
    context.startActivity(
        Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}.isSuccess
