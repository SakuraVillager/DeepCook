package com.deepcook.app.feature.cooking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.core.model.AiOverlay
import com.deepcook.app.core.model.CookTimer
import com.deepcook.app.core.model.DishNote
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
    private val speechBuffer = StringBuilder()
    private var user = UserData()
    private var playbook: Playbook? = null
    private var overlay: AiOverlay? = null
    private var aiJob: Job? = null

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
            loadOverlay()
        }
    }

    /**
     * Reads this dish's cached enrichment, and asks for one only when the recipe still has empty
     * fields. Runs silently: the answer bar never mentions it.
     */
    private suspend fun loadOverlay() {
        runCatching { overlays.get(recipe.id) }.getOrNull()?.let { overlay = it }
        if (overlay != null || enricher.missingFields(recipe).isEmpty()) return
        runCatching { enricher.enrich(recipe) }.getOrNull()?.let { filled ->
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
        speechBuffer.setLength(0)
        val step = recipe.steps[_state.value.stepIndex.coerceIn(recipe.steps.indices)]

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
            try {
                for (attempt in 1..MAX_ATTEMPTS) {
                    if (token != askToken) return@launch
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
                    ).collect { event ->
                        if (event is AiEvent.Delta) {
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
                    }
                    if (got || token != askToken) break
                    if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS)
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
                _state.update {
                    it.copy(ai = it.ai.copy(answer = "现在有点忙，先看这一步：${step.text}", thinking = false))
                }
                _speech.tryEmit("现在有点忙。")
                _speech.tryEmit(step.text)
            } else {
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

    /** Ends the session and stops the notification; the caller then leaves the page. */
    fun complete(onDone: () -> Unit) = viewModelScope.launch {
        askToken++
        aiJob?.cancel()
        runCatching { sessions.clear() }
        onDone()
    }

    private companion object {
        const val FIRST_TOKEN_TIMEOUT_MS = 2_500L
        const val RETRY_DELAY_MS = 1_000L
        const val MAX_ATTEMPTS = 2
        const val HISTORY_LIMIT = 10
    }
}
