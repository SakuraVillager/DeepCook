package com.deepcook.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.UserData
import com.deepcook.app.core.model.UserPreferences
import com.deepcook.app.data.RecipeRepository
import com.deepcook.app.data.UserDataRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CancellationException

data class AppUiState(
    val recipes: List<Recipe> = emptyList(),
    val user: UserData = UserData(),
    val loading: Boolean = true,
    val contentError: String? = null,
    val userError: String? = null,
)

data class AppMessage(val text: String, val undo: (suspend () -> Unit)? = null)

class AppViewModel(private val recipes: RecipeRepository, private val userData: UserDataRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state = mutableState.asStateFlow()
    private val messageChannel = Channel<AppMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var userJob: Job? = null
    private var recipeJob: Job? = null
    private val favoriteMutex = Mutex()

    init {
        loadRecipes()
        observeUserData()
    }

    fun loadRecipes() {
        recipeJob?.cancel()
        recipeJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, contentError = null)
            try {
                val loaded = recipes.recipes()
                mutableState.value = mutableState.value.copy(recipes = loaded, loading = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(loading = false, contentError = "菜谱暂时无法读取，请重试。")
            }
        }
    }

    fun observeUserData() {
        userJob?.cancel()
        userJob = viewModelScope.launch {
            userData.data.catch { error ->
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(userError = "本地收藏与设置暂时无法读取，原数据未被清除。")
            }.collect { data -> mutableState.value = mutableState.value.copy(user = data, userError = null) }
        }
    }

    fun favorite(id: String) {
        mutate {
            favoriteMutex.withLock {
                val previous = userData.data.first().favorites.find { it.recipeId == id }
                userData.setFavorite(id, previous == null)
                if (previous != null) {
                    messageChannel.send(
                        AppMessage("已取消收藏") { userData.setFavorite(id, true, previous.timestamp) },
                    )
                }
            }
        }
    }

    fun visit(id: String) = mutate { userData.recordVisit(id) }
    fun removeVisit(id: String) = mutate { userData.removeVisit(id) }
    fun preferences(transform: (UserPreferences) -> UserPreferences) = mutate { userData.updatePreferences(transform) }
    fun clearRecent() = mutate {
        userData.clearRecent()
        messageChannel.send(AppMessage("已清除最近浏览"))
    }
    fun clearFavorites() = mutate {
        userData.clearFavorites()
        messageChannel.send(AppMessage("已清空收藏"))
    }
    fun resetPreferences() = mutate {
        userData.resetPreferences()
        messageChannel.send(AppMessage("已恢复默认设置"))
    }
    fun undo(action: suspend () -> Unit) = mutate(action)

    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                messageChannel.send(AppMessage("保存失败，请重试。原有数据保持不变。"))
            }
        }
    }
}
