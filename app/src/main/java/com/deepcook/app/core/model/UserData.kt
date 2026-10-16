package com.deepcook.app.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

@Serializable
enum class ColorSource(val label: String) { KITCHEN("暖黄厨房"), DYNAMIC("系统动态色") }

@Serializable
enum class ReadingSize(val label: String, val detailSp: Int, val readerSp: Int) {
    STANDARD("标准", 16, 24),
    LARGE("大", 18, 28),
    EXTRA_LARGE("特大", 20, 32),
}

@Serializable
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.KITCHEN,
    val readingSize: ReadingSize = ReadingSize.STANDARD,
    val showStepImages: Boolean = true,
    val keepScreenOn: Boolean = false,
)

@Serializable
data class SavedRecipe(val recipeId: String, val timestamp: Long)

@Serializable
data class UserData(
    val favorites: List<SavedRecipe> = emptyList(),
    val recent: List<SavedRecipe> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
) {
    fun withFavorite(id: String, favorite: Boolean, now: Long): UserData = copy(
        favorites = if (favorite) {
            (favorites.filterNot { it.recipeId == id } + SavedRecipe(id, now)).sortedByDescending { it.timestamp }
        } else {
            favorites.filterNot { it.recipeId == id }
        },
    )

    fun withVisit(id: String, now: Long): UserData = copy(
        recent = (listOf(SavedRecipe(id, now)) + recent.filterNot { it.recipeId == id }).take(50),
    )
}
