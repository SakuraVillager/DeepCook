package com.deepcook.app.feature.authoring

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeDraft
import com.deepcook.app.data.CoverStore
import com.deepcook.app.data.lookup.LookupOutcome
import com.deepcook.app.data.lookup.RecipeLookup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

data class EditorState(
    val draft: RecipeDraft = RecipeDraft(),
    /** Decided before the first save so the id survives edits, favourites and the cover name. */
    val recipeId: String = "",
    val editing: Boolean = false,
    val looking: Boolean = false,
    /** Feedback about the last lookup or save, shown under the name field. */
    val notice: String? = null,
    val errors: List<String> = emptyList(),
    val importingCover: Boolean = false,
)

/**
 * Owns one editing session for a user-authored recipe.
 *
 * The online prefill is deliberately a separate, explicit step: the user confirms the dish name,
 * [prefill] asks the catalog, and whatever comes back is merged into the same editable draft.
 * Offline, unmatched and failed lookups all end with the form still usable, because a manual
 * recipe is never blocked on the network.
 */
class RecipeEditorViewModel(
    private val lookup: RecipeLookup,
    private val covers: CoverStore,
    id: String,
    existing: Recipe?,
) : ViewModel() {
    private val mutable = MutableStateFlow(
        EditorState(
            draft = existing?.let(RecipeDraft::from) ?: RecipeDraft(),
            recipeId = id,
            editing = existing != null,
        ),
    )
    val state = mutable.asStateFlow()

    /** False until a prefill has landed once; afterwards only empty fields are filled in. */
    private var prefilledOnce = false

    fun edit(transform: (RecipeDraft) -> RecipeDraft) = mutable.update {
        it.copy(draft = transform(it.draft), errors = emptyList())
    }

    /** Fills empty list sections (preparation/notes) from a prefill without touching the rest. */
    fun note(notice: String) = mutable.update { it.copy(notice = notice) }

    /**
     * Looks the typed name up and merges the result.
     *
     * The user's own title and chosen cover always win; every other field is overwritten on the
     * first prefill and only filled when blank afterwards, so re-running the search cannot
     * silently discard edits the user already made.
     */
    fun prefill() {
        val current = mutable.value
        if (current.looking) return
        val name = current.draft.title.trim()
        if (name.isEmpty()) {
            mutable.update { it.copy(notice = "先写上菜名，再来找资料。") }
            return
        }
        mutable.update { it.copy(looking = true, notice = null, errors = emptyList()) }
        viewModelScope.launch {
            val outcome = try {
                lookup.lookup(name)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                LookupOutcome.Failed("联网获取失败，可以稍后重试，或直接手动填写。")
            }
            when (outcome) {
                is LookupOutcome.Filled -> {
                    // The first prefill may replace the form; later ones only fill gaps.
                    val overwrite = !prefilledOnce
                    prefilledOnce = true
                    mutable.update { state ->
                        state.copy(
                            looking = false,
                            draft = merge(state.draft, outcome.draft, overwrite),
                            notice = "已按「${outcome.matchedName}」补全资料（来自 ${outcome.sourceName}），" +
                                "请按自己的做法检查并修改。",
                        )
                    }
                }
                LookupOutcome.NotFound -> mutable.update {
                    it.copy(looking = false, notice = "菜谱库里没有这道菜，直接手动填写就好。")
                }
                LookupOutcome.Offline -> mutable.update {
                    it.copy(looking = false, notice = "现在没有网络，先手动填写吧。")
                }
                is LookupOutcome.Failed -> mutable.update {
                    it.copy(looking = false, notice = outcome.message)
                }
            }
        }
    }

    private fun merge(current: RecipeDraft, filled: RecipeDraft, overwrite: Boolean): RecipeDraft {
        val title = current.title.ifBlank { filled.title }
        if (overwrite) {
            // First prefill: take the source text wholesale, keep what the user already chose.
            return filled.copy(title = title, cover = current.cover)
        }
        return current.copy(
            title = title,
            region = current.region,
            categories = current.categories.ifEmpty { filled.categories },
            minutes = current.minutes ?: filled.minutes,
            difficulty = current.difficulty ?: filled.difficulty,
            servings = current.servings.ifBlank { filled.servings },
            description = current.description.ifBlank { filled.description },
            preparation = current.preparation.ifEmpty { filled.preparation },
            ingredients = current.ingredients
                .takeIf { rows -> rows.any { it.name.isNotBlank() } }
                ?: filled.ingredients,
            steps = current.steps
                .takeIf { rows -> rows.any { it.title.isNotBlank() || it.text.isNotBlank() } }
                ?: filled.steps,
            notes = current.notes.ifEmpty { filled.notes },
            sourceUrl = current.sourceUrl ?: filled.sourceUrl,
            sourceName = current.sourceName ?: filled.sourceName,
            prefilled = current.prefilled || filled.prefilled,
        )
    }

    /** Copies the picked photo into the app and drops the one it replaces. */
    fun importCover(uri: Uri) {
        if (mutable.value.importingCover) return
        mutable.update { it.copy(importingCover = true, errors = emptyList()) }
        viewModelScope.launch {
            val stored = covers.import(uri)
            val previous = mutable.value.draft.cover
            if (stored == null) {
                mutable.update { it.copy(importingCover = false, errors = listOf("这张图片读不出来，换一张试试。")) }
                return@launch
            }
            mutable.update {
                it.copy(importingCover = false, draft = it.draft.copy(cover = stored), notice = null)
            }
            if (previous != null && previous != stored) covers.delete(previous)
        }
    }

    fun removeCover() {
        val previous = mutable.value.draft.cover ?: return
        mutable.update { it.copy(draft = it.draft.copy(cover = null)) }
        viewModelScope.launch { covers.delete(previous) }
    }

    /**
     * Validates and builds the recipe.
     *
     * @return the recipe to save, or null when the draft still has problems — those are put on
     *   [EditorState.errors] for the form to show.
     */
    fun buildRecipe(): Recipe? {
        val draft = mutable.value.draft
        val problems = draft.problems()
        if (problems.isNotEmpty()) {
            mutable.update { it.copy(errors = problems) }
            return null
        }
        return draft.toRecipe(mutable.value.recipeId)
    }

    /** Called after a successful save so a later edit knows it is no longer a new recipe. */
    fun markSaved(recipe: Recipe) = mutable.update {
        it.copy(editing = true, recipeId = recipe.id, notice = "已保存", errors = emptyList())
    }
}
