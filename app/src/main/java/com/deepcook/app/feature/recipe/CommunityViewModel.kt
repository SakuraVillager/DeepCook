package com.deepcook.app.feature.recipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.core.model.RecipeCommunity
import com.deepcook.app.data.CoverStore
import com.deepcook.app.data.KitchenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class CommunityUiState(
    val content: RecipeCommunity = RecipeCommunity(),
    val loading: Boolean = true,
    val error: String? = null,
    val busy: Boolean = false,
    val composer: String? = null,
    val text: String = "",
    val photo: String? = null,
    val score: Int = 8,
)

class CommunityViewModel(
    private val recipeId: String,
    private val saved: SavedStateHandle,
    private val store: KitchenStore,
    private val covers: CoverStore,
) : ViewModel() {
    private val mutable = MutableStateFlow(
        CommunityUiState(
            composer = saved["composer"],
            text = saved["text"] ?: "",
            photo = saved["photo"],
            score = saved["score"] ?: 8,
        ),
    )
    val state = mutable.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            store.observe(recipeId).catch { error ->
                if (error is CancellationException) throw error
                mutable.value = mutable.value.copy(loading = false, error = "本地互动数据无法读取，原数据未被清除。")
            }.collect { mutable.value = mutable.value.copy(content = it, loading = false, error = null) }
        }
    }

    fun open(kind: String) {
        if (mutable.value.busy) return
        saved["composer"] = kind
        mutable.value = mutable.value.copy(composer = kind, score = mutable.value.content.ownRating ?: 8, error = null)
    }

    fun text(value: String) {
        saved["text"] = value.take(2000)
        mutable.value = mutable.value.copy(text = value.take(2000))
    }

    fun score(value: Int) {
        saved["score"] = value
        mutable.value = mutable.value.copy(score = value)
    }

    fun dismiss() {
        if (mutable.value.busy) return
        val photo = mutable.value.photo
        clearDraft()
        if (photo != null) viewModelScope.launch { runCatching { covers.delete(photo) } }
    }

    private fun clearDraft() {
        saved["composer"] = null
        saved["text"] = ""
        saved["photo"] = null
        mutable.value = mutable.value.copy(composer = null, text = "", photo = null, error = null)
    }

    fun photo(uri: String) = perform {
        val imported = covers.import(uri) ?: error("照片无法读取，请重新选择。")
        val old = mutable.value.photo
        saved["photo"] = imported
        mutable.value = mutable.value.copy(photo = imported)
        if (old != null) runCatching { covers.delete(old) }
    }

    fun submit() = perform {
        val draft = mutable.value
        if (draft.composer == "rating") {
            store.rate(recipeId, draft.score)
        } else {
            store.post(recipeId, draft.composer == "submission", draft.text, draft.photo)
        }
        clearDraft()
    }

    fun delete(id: String) = perform {
        val photo = store.deletePost(id)
        if (photo != null) runCatching { covers.delete(photo) }
    }

    private fun perform(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                action()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.value = mutable.value.copy(error = "操作未完成，请重试。文字和照片仍已保留。")
            } finally {
                mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
}
