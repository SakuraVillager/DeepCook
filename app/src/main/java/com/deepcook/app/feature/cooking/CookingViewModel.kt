package com.deepcook.app.feature.cooking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.core.model.AiOverlay
import com.deepcook.app.core.model.CookTimer
import com.deepcook.app.core.model.DishNote
import com.deepcook.app.core.model.NoteSource
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.UserData
import com.deepcook.app.data.AiOverlayStore
import com.deepcook.app.data.CookSessionStore
import com.deepcook.app.data.UserDataRepository
import com.deepcook.app.data.ai.AiEvent
import com.deepcook.app.data.ai.AiExchange
import com.deepcook.app.data.ai.AiGateway
import com.deepcook.app.data.ai.AssetPlaybook
import com.deepcook.app.data.ai.ContextBuilder
import com.deepcook.app.data.ai.Playbook
import com.deepcook.app.data.ai.RecipeEnricher
import com.deepcook.app.data.ai.SafetyRules
import com.deepcook.app.data.ai.ZhipuGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Owns the position, timers, notes and assistant conversation of one cooking session.
 *
 * Session changes are written to [CookSessionStore] immediately, so leaving the page (or the
 * process being reclaimed) loses nothing. The assistant follows the agreed timing: at 2.5 s with
 * no first token the screen says "超时" and reads the current step, while the request keeps
 * running in the background; a new question cancels that request.
 */
class CookingViewModel(
    private val recipe: Recipe,
    private val sessions: CookSessionStore,
    private val ai: AiGateway,
    private val book: AssetPlaybook,
    private val userData: UserDataRepository,
    private val titles: Map<String, String>,
    private val overlays: AiOverlayStore,
    private val enricher: RecipeEnricher,
) : ViewModel() {
    data class AiUi(
        val question: String = "",
        val answer: String = "",
        val thinking: Boolean = false,
        /** True once the 2.5 s deadline passed and the step text is standing in for the answer. */
        val timedOut: Boolean = false,
        val history: List<AiExchange> = emptyList(),
    )

    data class UiState(
        val stepIndex: Int = 0,
        val timers: List<CookTimer> = emptyList(),
        val notes: List<DishNote> = emptyList(),
        /** True once the stored session is known, which is when the foreground service may start. */
        val ready: Boolean = false,
        val ai: AiUi = AiUi(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    /** Sentences for the system TTS, already split so playback can start before the answer ends. */
    private val _speech = MutableSharedFlow<String>(extraBufferCapacity = 24)
    val speech = _speech.asSharedFlow()

    private val contextBuilder = ContextBuilder()
    private val answerCache = AnswerCache()
    private val speechBuffer = StringBuilder()
    private var user = UserData()
    private var playbook: Playbook? = null
    private var overlay: AiOverlay? = null
    private var aiJob: Job? = null
    private var enrichJob: Job? = null

    /** Guards against a cancelled question writing over the newer one. */
    private var askToken = 0

    init {
        viewModelScope.launch {
            try {
                sessions.ensure(recipe.id, recipe.title)
                sessions.session.collect { session ->
                    if (session != null && session.recipeId == recipe.id) {
                        _state.update {
                            it.copy(
                                stepIndex = session.stepIndex.coerceIn(0, recipe.steps.lastIndex),
                                timers = session.timers.sortedBy { timer -> timer.endsAt },
                                ready = true,
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A progress file that cannot be read must not block the steps themselves.
                _state.update { it.copy(ready = true) }
            }
        }
        viewModelScope.launch {
            userData.data.catch { }.collect { loaded ->
                user = loaded
                _state.update { it.copy(notes = loaded.notesFor(recipe.id)) }
            }
        }
        viewModelScope.launch {
            runCatching { book.get() }.onSuccess { loaded -> playbook = loaded }
        }
        viewModelScope.launch {
            loadCachedOverlay()
            scheduleEnrichment()
        }
    }

    /**
     * Reads this dish's cached enrichment. A fresh lookup is deliberately not started here: the
     * free model allows one request at a time, and an enrichment call in flight makes the user's
     * own question fail with 429.
     */
    private suspend fun loadCachedOverlay() {
        runCatching { overlays.get(recipe.id) }.getOrNull()?.let { overlay = it }
    }

    /**
     * Starts the background enrichment only after the page has been quiet for a while, and the
     * job is cancelled the moment the user asks something.
     */
    private fun scheduleEnrichment() {
        if (overlay != null || enricher.missingFields(recipe).isEmpty()) return
        enrichJob?.cancel()
        enrichJob = viewModelScope.launch {
            delay(ENRICH_DELAY_MS)
            // A question always wins the single request slot.
            if (aiJob?.isActive == true) return@launch
            val filled = runCatching { enricher.enrich(recipe) }.getOrNull() ?: return@launch
            overlay = filled
            runCatching { overlays.put(filled) }
        }
    }

    fun goTo(index: Int) {
        val target = index.coerceIn(0, recipe.steps.lastIndex)
        if (target == _state.value.stepIndex) return
        _state.update { it.copy(stepIndex = target) }
        viewModelScope.launch { runCatching { sessions.setStep(recipe.id, target) } }
    }

    fun addTimer(minutes: Int) = viewModelScope.launch {
        runCatching { sessions.addTimer(recipe.id, minutes) }
    }

    fun cancelTimer(id: String) = viewModelScope.launch {
        runCatching { sessions.removeTimer(id) }
    }

    /** Fixed answers (playbook, safety) return at once; everything else streams from the model. */
    fun ask(raw: String) {
        if (!_state.value.ready) return
        val question = raw.trim()
        if (question.isEmpty()) return

        askToken++
        val token = askToken
        aiJob?.cancel()
        enrichJob?.cancel()
        enrichJob = null
        speechBuffer.setLength(0)
        val step = recipe.steps[_state.value.stepIndex.coerceIn(recipe.steps.indices)]

        // "记一笔：少放盐" is a note for this dish, handled without the model.
        val noted = NoteCommand.extract(question)
        if (noted != null) {
            val note = DishNote(
                id = UUID.randomUUID().toString(),
                recipeId = recipe.id,
                text = noted,
                raw = question,
                updatedAt = System.currentTimeMillis(),
                source = NoteSource.USER,
            )
            viewModelScope.launch { runCatching { userData.addDishNote(note) } }
            val reply = "记下了：$noted"
            _speech.tryEmit(reply)
            _state.update {
                it.copy(
                    ai = it.ai.copy(
                        question = question,
                        answer = reply,
                        thinking = false,
                        timedOut = false,
                        history = (it.ai.history + AiExchange(question, reply)).takeLast(HISTORY_LIMIT),
                    ),
                )
            }
            return
        }

        // "计时十分钟" is deterministic; handle it before any model call.
        val timerMinutes = CookCommand.timerMinutes(question)
        if (timerMinutes != null) {
            addTimer(timerMinutes)
            val reply = "好，计时 $timerMinutes 分钟。"
            _speech.tryEmit(reply)
            _state.update {
                it.copy(
                    ai = it.ai.copy(
                        question = question,
                        answer = reply,
                        thinking = false,
                        timedOut = false,
                        history = (it.ai.history + AiExchange(question, reply)).takeLast(HISTORY_LIMIT),
                    ),
                )
            }
            return
        }

        val quick = playbook?.match(question)?.answer
            ?: SafetyRules.answer(question, recipe, user.profile.avoids)
        if (quick != null) {
            _speech.tryEmit(quick)
            _state.update {
                it.copy(
                    ai = it.ai.copy(
                        question = question,
                        answer = quick,
                        thinking = false,
                        timedOut = false,
                        history = (it.ai.history + AiExchange(question, quick)).takeLast(HISTORY_LIMIT),
                    ),
                )
            }
            return
        }

        // Ask the same thing twice on the same step and the earlier answer is reused, so the cook
        // does not wait for the model again. Cache is per dish session and bounded.
        val cacheKey = answerCache.key(_state.value.stepIndex, question)
        answerCache.get(cacheKey)?.let { cached ->
            _speech.tryEmit(cached)
            _state.update {
                it.copy(
                    ai = it.ai.copy(
                        question = question,
                        answer = cached,
                        thinking = false,
                        timedOut = false,
                        history = (it.ai.history + AiExchange(question, cached)).takeLast(HISTORY_LIMIT),
                    ),
                )
            }
            return
        }

        _state.update {
            it.copy(ai = it.ai.copy(question = question, answer = "", thinking = true, timedOut = false))
        }
        aiJob = viewModelScope.launch {
            val timeout = launch {
                delay(FIRST_TOKEN_TIMEOUT_MS)
                if (token == askToken && _state.value.ai.answer.isEmpty()) {
                    _state.update {
                        it.copy(ai = it.ai.copy(answer = "超时。先看这一步：${step.text}", timedOut = true))
                    }
                    _speech.tryEmit("超时。")
                    _speech.tryEmit(step.text)
                }
            }
            var got = false
            var failure: AiEvent? = null
            try {
                for (attempt in 1..MAX_ATTEMPTS) {
                    if (token != askToken) return@launch
                    // When the primary free model is queued, the second attempt uses the fallback.
                    val model = if (attempt > 1 && failure == AiEvent.Busy) {
                        ZhipuGateway.FALLBACK_MODEL
                    } else {
                        null
                    }
                    ai.stream(
                        contextBuilder.messages(
                            recipe = recipe,
                            stepIndex = _state.value.stepIndex,
                            user = user,
                            titles = titles,
                            question = question,
                            history = _state.value.ai.history,
                            overlay = overlay,
                        ),
                        model = model,
                    ).collect { event ->
                        when (event) {
                            is AiEvent.Delta -> {
                                if (!got) timeout.cancel()
                                got = true
                                if (token == askToken) {
                                    speechBuffer.append(event.text)
                                    drainSpeech()
                                    _state.update { current ->
                                        val base = if (current.ai.timedOut) "" else current.ai.answer
                                        current.copy(
                                            ai = current.ai.copy(
                                                answer = base + event.text,
                                                thinking = false,
                                                timedOut = false,
                                            ),
                                        )
                                    }
                                }
                            }

                            else -> failure = event
                        }
                    }
                    if (got || token != askToken) break
                    if (attempt < MAX_ATTEMPTS && failure != AiEvent.Busy) delay(RETRY_DELAY_MS)
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                timeout.cancel()
            }
            if (token != askToken) return@launch
            speechBuffer.toString().trim().takeIf { it.isNotEmpty() }?.let { _speech.tryEmit(it) }
            speechBuffer.setLength(0)
            val answer = _state.value.ai.answer
            if (answer.isEmpty()) {
                val (prefix, text) = failureText(failure, step.text)
                _state.update {
                    it.copy(ai = it.ai.copy(answer = "$prefix，先看这一步：$text", thinking = false))
                }
                _speech.tryEmit(prefix)
                _speech.tryEmit(text)
            } else {
                answerCache.put(cacheKey, answer)
                _state.update {
                    it.copy(
                        ai = it.ai.copy(
                            thinking = false,
                            history = (it.ai.history + AiExchange(question, answer)).takeLast(HISTORY_LIMIT),
                        ),
                    )
                }
            }
        }
    }

    /** Splits streamed text into whole sentences so TTS can start before the answer ends. */
    private fun drainSpeech() {
        while (true) {
            val index = speechBuffer.indexOfFirst { it in "。！？!?\n" }
            if (index < 0) return
            val sentence = speechBuffer.substring(0, index + 1).trim()
            speechBuffer.delete(0, index + 1)
            if (sentence.isNotEmpty()) _speech.tryEmit(sentence)
        }
    }

    /** Turns the last transport failure into something the screen can say out loud. */
    private fun failureText(failure: AiEvent?, step: String): Pair<String, String> = when (failure) {
        AiEvent.Busy -> "AI 正忙（免费模型排队中），过几秒再问一次" to step
        AiEvent.Offline -> "手机没网" to step
        is AiEvent.Failed -> when (failure.status) {
            401, 403 -> "AI 认证失败（${failure.status}），检查 key" to step
            null -> "连不上 AI" to step
            else -> "AI 出错了（${failure.status}）" to step
        }

        else -> "AI 没回应" to step
    }

    /** Ends the session and stops the notification; the caller then leaves the page. */
    fun complete(onDone: () -> Unit) = viewModelScope.launch {
        askToken++
        aiJob?.cancel()
        enrichJob?.cancel()
        runCatching { sessions.clear() }
        onDone()
    }

    private companion object {
        const val FIRST_TOKEN_TIMEOUT_MS = 2_500L
        const val RETRY_DELAY_MS = 1_000L
        const val ENRICH_DELAY_MS = 6_000L
        const val MAX_ATTEMPTS = 2
        const val HISTORY_LIMIT = 10
    }
}
