package com.deepcook.app.feature.authoring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.core.model.*
import com.deepcook.app.data.RecipeCovers
import com.deepcook.app.data.UserRecipeStore
import com.deepcook.app.data.lookup.LookupOutcome
import com.deepcook.app.data.lookup.RecipeLookup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class EditorState(
    val draft: RecipeDraft = RecipeDraft(),
    val recipeId: String = "",
    val editing: Boolean = false,
    val looking: Boolean = false,
    val notice: String? = null,
    val errors: List<String> = emptyList(),
    val importingCover: Boolean = false,
    val saving: Boolean = false,
    val dirty: Boolean = false,
)

/** A save commits data before leaving; cover replacement is staged until that commit succeeds. */
class RecipeEditorViewModel(
    private val lookup: RecipeLookup,
    private val covers: RecipeCovers,
    private val store: UserRecipeStore,
    id: String,
    existing: Recipe?,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val initial = existing?.let(RecipeDraft::from) ?: RecipeDraft()
    private val restored = saved.get<String>("draft")?.let {
        runCatching { json.decodeFromString<RecipeDraft>(it) }.getOrNull()
    }
    private val mutable = MutableStateFlow(
        EditorState(
            restored ?: initial,
            id,
            existing != null || saved.get<Boolean>("editing") == true,
            dirty =
            saved["dirty"] ?: false,
        ),
    )
    val state = mutable.asStateFlow()
    private val finishedChannel = Channel<Unit>(Channel.BUFFERED)
    val finished = finishedChannel.receiveAsFlow()
    private var lookupJob: Job? = null
    private var originalCover: String? = if (saved.contains(
            "originalCover",
        )
    ) {
        saved["originalCover"]
    } else {
        existing?.cover
    }
    private val staged = (saved.get<ArrayList<String>>("stagedCovers") ?: arrayListOf()).toMutableSet()

    init {
        saved["originalCover"] = originalCover
        saved["editing"] = mutable.value.editing
    }

    private fun persistDraft() {
        saved["draft"] = json.encodeToString(mutable.value.draft)
        saved["dirty"] = mutable.value.dirty
        saved["stagedCovers"] = ArrayList(staged)
    }

    fun edit(transform: (RecipeDraft) -> RecipeDraft) {
        if (mutable.value.saving) return
        val previous = mutable.value.draft
        val next = transform(previous)
        if (next.title.trim() != previous.title.trim()) lookupJob?.cancel()
        mutable.update {
            it.copy(
                draft = next,
                dirty = it.dirty || next != previous,
                errors = emptyList(),
                looking = if (next.title.trim() != previous.title.trim()) false else it.looking,
            )
        }
        persistDraft()
    }

    fun note(notice: String) = mutable.update { it.copy(notice = notice) }

    fun prefill() {
        if (mutable.value.looking || mutable.value.saving) return
        val name = mutable.value.draft.title.trim()
        if (name.isEmpty()) {
            note("先写上菜名，再来找资料。")
            return
        }
        mutable.update { it.copy(looking = true, notice = null, errors = emptyList()) }
        lookupJob = viewModelScope.launch {
            val outcome = try {
                lookup.lookup(name)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                LookupOutcome.Failed("资料暂时无法读取，可以重试或手动填写。")
            }
            if (mutable.value.draft.title.trim() != name) return@launch
            when (outcome) {
                is LookupOutcome.Filled -> {
                    mutable.update { state ->
                        state.copy(
                            looking = false,
                            draft = fillBlanks(state.draft, outcome.draft),
                            dirty = true,
                            notice = "已按「${outcome.matchedName}」补全空白资料（${outcome.sourceName}），请核对后保存。",
                        )
                    }
                    persistDraft()
                }
                LookupOutcome.NotFound -> mutable.update { it.copy(looking = false, notice = "菜谱库里没有这道菜，可以手动填写。") }
                LookupOutcome.Offline -> mutable.update {
                    it.copy(looking = false, notice = "本地没有匹配资料，当前网络不可用，可以手动填写。")
                }
                is LookupOutcome.Failed -> mutable.update { it.copy(looking = false, notice = outcome.message) }
            }
        }
    }

    fun importCover(uri: String) {
        if (mutable.value.importingCover || mutable.value.saving) return
        mutable.update { it.copy(importingCover = true) }
        viewModelScope.launch {
            try {
                val stored = covers.import(uri)
                if (stored == null) {
                    mutable.update { it.copy(importingCover = false, errors = listOf("图片无法读取，请换一张。")) }
                } else {
                    staged += stored
                    mutable.update {
                        it.copy(importingCover = false, draft = it.draft.copy(cover = stored), dirty = true)
                    }
                    persistDraft()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(importingCover = false, errors = listOf("图片保存失败，请重试。")) }
            }
        }
    }

    fun removeCover() = edit { it.copy(cover = null) }

    fun save() {
        val current = mutable.value
        if (current.saving || current.importingCover || current.looking) return
        val errors = current.draft.problems()
        if (errors.isNotEmpty()) {
            mutable.update { it.copy(errors = errors) }
            return
        }
        mutable.update { it.copy(saving = true, errors = emptyList()) }
        viewModelScope.launch {
            try {
                val recipe = current.draft.toRecipe(current.recipeId)
                store.save(recipe)
                // From this point saving succeeded. Cleanup failure must not be reported as a failed save.
                withContext(NonCancellable) {
                    val obsolete = staged + listOfNotNull(originalCover)
                    obsolete.filter { it != recipe.cover }.forEach { cover -> runCatching { covers.delete(cover) } }
                    staged.clear()
                    originalCover = recipe.cover
                    saved["originalCover"] = recipe.cover
                    mutable.update { it.copy(saving = false, dirty = false, editing = true) }
                    persistDraft()
                    finishedChannel.send(Unit)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(saving = false, errors = listOf("保存失败，填写内容和原封面仍保留，请重试。")) }
            }
        }
    }

    fun discard() {
        if (mutable.value.saving || mutable.value.importingCover) return
        lookupJob?.cancel()
        mutable.update { it.copy(saving = true) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                staged.filter { it != originalCover }.forEach { cover -> runCatching { covers.delete(cover) } }
                staged.clear()
                finishedChannel.send(Unit)
            }
        }
    }

    companion object {
        /** Every lookup, including the first, fills gaps only. In-flight user edits always win. */
        fun fillBlanks(current: RecipeDraft, filled: RecipeDraft): RecipeDraft = current.copy(
            title = current.title.ifBlank { filled.title },
            region = current.region.takeUnless { it == NO_CONTENT } ?: filled.region,
            categories = current.categories.ifEmpty { filled.categories },
            minutes = current.minutes ?: filled.minutes,
            difficulty = current.difficulty ?: filled.difficulty,
            servings = current.servings.ifBlank { filled.servings },
            description = current.description.ifBlank { filled.description },
            ingredients =
            current.ingredients.takeIf { rows -> rows.any { it.name.isNotBlank() || it.amount.isNotBlank() } }
                ?: filled.ingredients,
            steps =
            current.steps.takeIf { rows ->
                rows.any {
                    it.title.isNotBlank() || it.text.isNotBlank() ||
                        it.cue.isNotBlank()
                }
            }
                ?: filled.steps,
            preparation = current.preparation.ifEmpty { filled.preparation },
            notes = current.notes.ifEmpty { filled.notes },
            sourceUrl = current.sourceUrl ?: filled.sourceUrl,
            sourceName = current.sourceName ?: filled.sourceName,
            originalSource = current.originalSource ?: filled.originalSource,
            extraTags = current.extraTags.ifEmpty { filled.extraTags },
            aliases = current.aliases.ifEmpty { filled.aliases },
            prefilled = current.prefilled || filled.prefilled,
        )
    }
}
