package com.deepcook.app.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.core.model.FoodProfile
import com.deepcook.app.data.UserDataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class FoodProfileUiState(
    val profile: FoodProfile = FoodProfile(),
    val page: Int = 0,
    val loading: Boolean = true,
    val ready: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

/** Serial writes keep an earlier draft from arriving after the final save. */
class FoodProfileViewModel(
    private val repository: UserDataRepository,
    private val saved: SavedStateHandle,
    val editing: Boolean,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FoodProfileUiState())
    val state = mutableState.asStateFlow()
    private data class Write(val profile: FoodProfile, val page: Int, val finish: Boolean)
    private val writes = Channel<Write>(Channel.UNLIMITED)

    init {
        load()
        viewModelScope.launch {
            for (write in writes) {
                try {
                    repository.updateFoodSetup { setup ->
                        if (write.finish) {
                            setup.copy(profile = write.profile, draft = null, page = 0, completed = true)
                        } else {
                            setup.copy(draft = write.profile, page = write.page)
                        }
                    }
                    if (write.finish) {
                        mutableState.value =
                            mutableState.value.copy(saving = false, done = true, error = null)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    mutableState.value = mutableState.value.copy(
                        saving = if (write.finish) false else mutableState.value.saving,
                        error = "未能保存，请重试。你的选择仍保留在当前页面。",
                    )
                }
            }
        }
    }

    fun load() {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val setup = repository.data.first().foodSetup
                val draft = saved.get<String>("food-profile")?.let { Json.decodeFromString<FoodProfile>(it) }
                    ?: if (editing) setup.profile else setup.draft ?: setup.profile
                mutableState.value = FoodProfileUiState(
                    profile = draft,
                    page = (saved.get<Int>("food-page") ?: if (editing) 0 else setup.page).coerceIn(0, 3),
                    loading = false,
                    ready = true,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(loading = false, error = "暂时无法读取饮食偏好，请重试。")
            }
        }
    }

    fun change(transform: (FoodProfile) -> FoodProfile) {
        if (!state.value.ready || state.value.saving || state.value.done) return
        mutableState.value = state.value.copy(profile = transform(state.value.profile), error = null)
        rememberDraft()
    }

    fun page(page: Int) {
        if (state.value.saving || state.value.done) return
        mutableState.value = state.value.copy(page = page.coerceIn(0, 3))
        rememberDraft()
    }

    private fun rememberDraft() {
        val snapshot = state.value
        saved["food-profile"] = Json.encodeToString(snapshot.profile)
        saved["food-page"] = snapshot.page
        if (!editing) writes.trySend(Write(snapshot.profile, snapshot.page, false))
    }

    fun finish() {
        val snapshot = state.value
        if (!snapshot.ready || snapshot.saving || snapshot.done) return
        mutableState.value = snapshot.copy(saving = true, error = null)
        writes.trySend(Write(snapshot.profile, snapshot.page, true))
    }
}
