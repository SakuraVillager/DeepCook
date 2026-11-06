package com.deepcook.app.feature.cooking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Wraps the system speech recognizer. No audio ever leaves the app through this class beyond
 * what the platform's own recognition service does; the app itself sends only the final text.
 */
class VoiceInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    fun isListening(): Boolean = listening

    fun start(onText: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("这台手机没有语音识别服务，先打字吧。")
            return
        }
        stop()
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onError(error: Int) {
                listening = false
                onError(messageFor(error))
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (text.isNotEmpty()) onText(text) else onError("没听清，再说一遍。")
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        runCatching { speech.startListening(intent) }
            .onFailure {
                listening = false
                onError("麦克风没能启动。")
            }
        listening = true
    }

    fun stop() {
        val speech = recognizer ?: return
        runCatching { speech.stopListening() }
        runCatching { speech.cancel() }
        runCatching { speech.destroy() }
        recognizer = null
        listening = false
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "麦克风出问题了。"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限。"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别要联网，现在连不上。"
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听清，再说一遍。"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别正忙，稍后再试。"
        else -> "没听清，再说一遍。"
    }
}

/**
 * System text-to-speech at 1.3x. Utterances queue, so a streamed answer can be handed over one
 * sentence at a time and starts speaking before the model has finished.
 */
class TtsSpeaker(context: Context) {
    private var engine: TextToSpeech? = null

    @Volatile
    private var ready = false
    private val pending = ArrayDeque<String>()

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                runCatching { engine?.language = Locale.SIMPLIFIED_CHINESE }
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
