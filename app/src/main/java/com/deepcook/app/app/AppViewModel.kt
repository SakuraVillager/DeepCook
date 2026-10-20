package com.deepcook.app.app

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.UserData
import com.deepcook.app.core.model.UserPreferences
import com.deepcook.app.core.model.UserProfile
import com.deepcook.app.data.AvatarStore
import com.deepcook.app.data.CoverStore
import com.deepcook.app.data.RecipeRepository
import com.deepcook.app.data.UserDataRepository
import com.deepcook.app.data.UserRecipeStore
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
import java.io.IOException
import java.util.concurrent.CancellationException

data class AppUiState(
    /** The bundled catalog plus the user's own recipes, in that order. */
    val recipes: List<Recipe> = emptyList(),
    /** Just the user's own recipes, newest first, for the profile page. */
    val myRecipes: List<Recipe> = emptyList(),
    val user: UserData = UserData(),
    val loading: Boolean = true,
    val contentError: String? = null,
    val userError: String? = null,
    val userRecipeError: String? = null,
)

data class AppMessage(val text: String, val undo: (suspend () -> Unit)? = null)

class AppViewModel(
    private val recipes: RecipeRepository,
    private val userData: UserDataRepository,
    private val userRecipes: UserRecipeStore,
    private val covers: CoverStore,
    private val avatars: AvatarStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state = mutableState.asStateFlow()
    private val messageChannel = Channel<AppMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var userJob: Job? = null
    private var recipeJob: Job? = null
    private var userRecipeJob: Job? = null
    private val favoriteMutex = Mutex()

    /** Kept apart from the state so a change in either source can rebuild the merged list. */
    private var bundled: List<Recipe> = emptyList()
    private var mine: List<Recipe> = emptyList()

    init {
        loadRecipes()
        observeUserData()
        observeUserRecipes()
    }

    fun loadRecipes() {
        recipeJob?.cancel()
        recipeJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, contentError = null)
            try {
                bundled = recipes.recipes()
                publish()
                mutableState.value = mutableState.value.copy(loading = false)
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

    fun observeUserRecipes() {
        userRecipeJob?.cancel()
        userRecipeJob = viewModelScope.launch {
            userRecipes.recipes.catch { error ->
                if (error is CancellationException) throw error
                // The file is left untouched; the bundled catalog stays usable.
                mutableState.value = mutableState.value.copy(
                    userRecipeError = "我的菜谱暂时无法读取，文件没有被清除。",
                )
            }.collect { loaded ->
                mine = loaded
                publish()
                mutableState.value = mutableState.value.copy(userRecipeError = null)
            }
        }
    }

    /** Rebuilds the merged catalog from both sources without touching unrelated state. */
    private fun publish() {
        mutableState.value = mutableState.value.copy(
            recipes = bundled + mine,
            myRecipes = mine.reversed(),
        )
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
    fun updateProfile(transform: (UserProfile) -> UserProfile) = mutate { userData.updateProfile(transform) }

    /** Decodes the picked photo for the cropper; null when the file cannot be read. */
    suspend fun avatarSource(uri: String): Bitmap? = avatars.decode(uri)

    /** Stores the cropped square and points the profile at it, replacing the old file. */
    fun saveAvatar(bitmap: Bitmap) = viewModelScope.launch {
        val previous = mutableState.value.user.profile.avatar
        try {
            val saved = avatars.save(bitmap, previous) ?: throw IOException("头像文件写入失败")
            try {
                userData.updateProfile { it.copy(avatar = saved) }
            } catch (error: Exception) {
                runCatching { avatars.delete(saved) }
                throw error
            }
            messageChannel.send(AppMessage("头像已更新"))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            messageChannel.send(AppMessage("头像没能保存，请换一张试试。"))
        }
    }

    fun removeAvatar() {
        val previous = mutableState.value.user.profile.avatar
        mutate {
            userData.updateProfile { it.copy(avatar = null) }
            runCatching { avatars.delete(previous) }
            messageChannel.send(AppMessage("已移除头像"))
        }
    }

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

    /** Deletes a user recipe, its cover file, and its traces in favourites and history. */
    fun deleteRecipe(id: String) = mutate {
        val cover = mine.find { it.id == id }?.cover
        require(id.startsWith(com.deepcook.app.core.model.USER_RECIPE_ID_PREFIX))
        userData.forget(id)
        userRecipes.delete(id)
        runCatching { covers.delete(cover) }
        messageChannel.send(AppMessage("已删除这道菜谱"))
    }

    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                messageChannel.send(AppMessage("操作未完成，请重试。"))
            }
        }
    }
}
