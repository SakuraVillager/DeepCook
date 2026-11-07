package com.deepcook.app.feature.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.core.model.AiOverlay
import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.UserData
import com.deepcook.app.data.AiOverlayStore
import com.deepcook.app.data.UserDataRepository
import com.deepcook.app.data.ai.AiEvent
import com.deepcook.app.data.ai.AiGateway
import com.deepcook.app.data.ai.DishAnswer
import com.deepcook.app.data.ai.DishContextBuilder
import com.deepcook.app.data.ai.ServingScale
import com.deepcook.app.data.ai.Substitutions
import com.deepcook.app.data.ai.ZhipuGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The detail page's own assistant: serving maths, a missing ingredient, or "why did mine go wrong".
 *
 * Local answers win. Scaling is computed, not asked; the common swaps come from [Substitutions];
 * only the questions left are sent to the model. When the free model is queued or offline, the page
 * falls back to the recipe's own words instead of an empty card, and the same 2.5 s deadline as the
 * cooking screen keeps a tap from looking like nothing happened.
 */
class DishAiViewModel(
    private val recipe: Recipe,
    private val ai: AiGateway,
    userData: UserDataRepository,
    private val overlays: AiOverlayStore,
) : ViewModel() {
    enum class Source {
        /** Streamed from the model. */
        AI,

        /** A fixed local answer: serving maths or a known pantry swap. */
        LOCAL,

        /** The recipe's own text, shown because the model did not answer. */
        FALLBACK,
    }

    data class Answer(
        val asked: String,
        val text: String = "",
        val hint: String? = null,
        val thinking: Boolean = false,
        val source: Source = Source.AI,
    )

    data class UiState(
        val servingsBase: Double? = null,
        val servingsLabel: String = "",
        /** null keeps the printed amounts. */
        val targetServings: Int? = null,
        val answer: Answer? = null,
    ) {
        val factor: Double
            get() {
                val base = servingsBase ?: return 1.0
                val target = targetServings ?: return 1.0
                return ServingScale.factor(base, target)
            }

        val servingsOptions: List<Int> get() = servingsBase?.let(ServingScale::options) ?: emptyList()
    }

    private data class Pending(val asked: String, val question: String, val fallback: String)

    private val _state = MutableStateFlow(
        UiState(
            servingsBase = ServingScale.parse(recipe.servings),
            servingsLabel = ServingScale.label(recipe.servings),
        ),
    )
    val state = _state.asStateFlow()

    private val builder = DishContextBuilder()
    private var user = UserData()
    private var overlay: AiOverlay? = null
    private var pending: Pending? = null
    private var job: Job? = null

    /** Guards against a cancelled question writing over the newer one. */
    private var token = 0

    init {
        viewModelScope.launch {
            userData.data.catch { }.collect { loaded -> user = loaded }
        }
        viewModelScope.launch {
            // A cached enrichment can supply the servings a recipe left as "无".
            overlay = runCatching { overlays.get(recipe.id) }.getOrNull()
            refreshServings()
        }
    }

    private fun refreshServings() {
        val servings = overlay?.servings?.takeIf { it.isNotBlank() } ?: recipe.servings
        val base = ServingScale.parse(servings)
        _state.update { current ->
            current.copy(
                servingsBase = base,
                servingsLabel = ServingScale.label(servings),
                targetServings = current.targetServings?.takeIf { base != null },
            )
        }
    }

    /** null restores the printed amounts. */
    fun selectServings(target: Int?) {
        _state.update { current ->
            when {
                target == null -> current.copy(targetServings = null)
                current.servingsBase == null -> current
                target <= 0 -> current
                else -> current.copy(targetServings = target)
            }
        }
    }

    /** The amount to print for [ingredient] under the current serving choice. */
    fun amountOf(ingredient: Ingredient): String {
        val printed = printedAmount(ingredient)
        val state = _state.value
        val base = state.servingsBase ?: return printed
        val target = state.targetServings ?: return printed
        return ServingScale.scale(printed, ServingScale.factor(base, target))
    }

    /** What the recipe (or its cached enrichment) actually says, before any serving scaling. */
    private fun printedAmount(ingredient: Ingredient): String =
        DishContextBuilder.ingredientAmount(recipe, overlay, ingredient.id, ingredient.amount)

    /** "没有蚝油用什么" — answered locally for common pantry items, by the model otherwise. */
    fun askSubstitute(ingredient: Ingredient) {
        val name = ingredient.name.trim()
        if (name.isEmpty()) return
        val asked = "没有「$name」用什么替代？"
        val local = Substitutions.find(name)
        if (local != null) {
            publishLocal(asked, local)
            return
        }
        ask(asked, "没有$name，这道菜可以用什么代替？", DishAnswer.missingIngredientHint(name, printedAmount(ingredient)))
    }

    /** The free-text box: a failed attempt, or any other question about the dish. */
    fun askDiagnosis(raw: String) {
        val question = raw.trim()
        if (question.isEmpty()) return
        // "没有蚝油怎么办" typed into the box still gets the instant local answer.
        if (SubstitutionIntent.any { it in question }) {
            Substitutions.find(question)?.let { local ->
                publishLocal(question, local)
                return
            }
        }
        ask(question, question, DishAnswer.recipeFallback(recipe))
    }

    fun retry() {
        val last = pending ?: return
        ask(last.asked, last.question, last.fallback)
    }

    fun dismiss() {
        cancelRunning()
        _state.update { it.copy(answer = null) }
    }

    private fun publishLocal(asked: String, text: String) {
        cancelRunning()
        pending = null
        _state.update { it.copy(answer = Answer(asked = asked, text = text, source = Source.LOCAL)) }
    }

    private fun cancelRunning() {
        token++
        job?.cancel()
        job = null
    }

    /**
     * Streams one model answer, keeping [fallback] on screen if no token arrives within the
     * deadline. The request is not cancelled at the deadline: the answer still replaces the
     * fallback when the free model finally starts talking.
     */
    private fun ask(asked: String, question: String, fallback: String) {
        pending = Pending(asked, question, fallback)
        cancelRunning()
        val myToken = token
        _state.update { it.copy(answer = Answer(asked = asked, thinking = true)) }
        job = viewModelScope.launch {
            var got = false
            var failure: AiEvent? = null
            val deadline = launch {
                delay(FIRST_TOKEN_TIMEOUT_MS)
                if (myToken == token && _state.value.answer?.text.isNullOrEmpty()) {
                    _state.update { current ->
                        current.copy(
                            answer = current.answer?.copy(
                                text = DishAnswer.twoSentences(fallback),
                                hint = TIMEOUT_HINT,
                                thinking = true,
                                source = Source.FALLBACK,
                            ),
                        )
                    }
                }
            }
            try {
                for (attempt in 1..MAX_ATTEMPTS) {
                    if (myToken != token) return@launch
                    // When the primary free model is queued, the second attempt uses the fallback.
                    val model = if (attempt > 1 && failure == AiEvent.Busy) {
                        ZhipuGateway.FALLBACK_MODEL
                    } else {
                        null
                    }
                    ai.stream(builder.messages(recipe, user, question, overlay), model = model).collect { event ->
                        when (event) {
                            is AiEvent.Delta -> {
                                if (!got) deadline.cancel()
                                got = true
                                if (myToken == token) {
                                    _state.update { current ->
                                        val answer = current.answer ?: return@update current
                                        // The fallback text is replaced, not appended to.
                                        val base = if (answer.source == Source.FALLBACK) "" else answer.text
                                        current.copy(
                                            answer = answer.copy(
                                                text = base + event.text,
                                                hint = null,
                                                thinking = false,
                                                source = Source.AI,
                                            ),
                                        )
                                    }
                                }
                            }

                            else -> failure = event
                        }
                    }
                    if (got || myToken != token) break
                    if (attempt < MAX_ATTEMPTS && failure != AiEvent.Busy) delay(RETRY_DELAY_MS)
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                deadline.cancel()
            }
            if (myToken != token) return@launch
            val current = _state.value.answer ?: return@launch
            val shaped = if (got) DishAnswer.twoSentences(current.text) else ""
            if (shaped.isEmpty()) {
                _state.update {
                    it.copy(
                        answer = current.copy(
                            text = DishAnswer.twoSentences(fallback),
                            hint = failureHint(failure) ?: current.hint,
                            thinking = false,
                            source = Source.FALLBACK,
                        ),
                    )
                }
            } else {
                _state.update {
                    it.copy(answer = current.copy(text = shaped, hint = null, thinking = false, source = Source.AI))
                }
            }
        }
    }

    private fun failureHint(failure: AiEvent?): String? = when (failure) {
        AiEvent.Busy -> "$BUSY_HINT，下面是菜谱里的内容"
        AiEvent.Offline -> "手机没网，下面是菜谱里的内容"
        is AiEvent.Failed -> when (failure.status) {
            401, 403 -> "AI 认证失败（${failure.status}）"
            null -> "连不上 AI，下面是菜谱里的内容"
            else -> "AI 出错了（${failure.status}），下面是菜谱里的内容"
        }

        else -> null
    }

    private companion object {
        const val FIRST_TOKEN_TIMEOUT_MS = 2_500L
        const val RETRY_DELAY_MS = 1_000L
        const val MAX_ATTEMPTS = 2
        const val TIMEOUT_HINT = "AI 正在排队，下面是菜谱里的内容"
        const val BUSY_HINT = "AI 正忙（免费模型排队中）"

        /** Words that make a typed question a "what can I use instead" question. */
        val SubstitutionIntent = listOf("没有", "代替", "替代", "用什么", "少了", "缺")
    }
}
