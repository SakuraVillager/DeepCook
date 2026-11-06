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
    /** When off, the cooking screen hides the microphone and the user types instead. */
    val voiceInputEnabled: Boolean = true,
    /** When off, answers stay on screen and the system voice stays silent. */
    val speakAnswers: Boolean = true,
    /** Name of the system TTS voice, or null for the engine default. */
    val ttsVoice: String? = null,
    /** Friendly label for [ttsVoice], kept so Settings can show it without re-querying the engine. */
    val ttsVoiceLabel: String? = null,
)

@Serializable
data class SavedRecipe(val recipeId: String, val timestamp: Long)

@Serializable
data class UserData(
    val favorites: List<SavedRecipe> = emptyList(),
    val recent: List<SavedRecipe> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    /** Name, picture and taste tags; defaulted so a file written before this field still loads. */
    val profile: UserProfile = UserProfile(),
    /** Per-dish corrections the user asked to remember; defaulted for older files. */
    val dishNotes: List<DishNote> = emptyList(),
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

    /** Notes for one dish, newest first. */
    fun notesFor(recipeId: String): List<DishNote> =
        dishNotes.filter { it.recipeId == recipeId }.sortedByDescending { it.updatedAt }

    /** Adds or replaces a note by id, dropping an older note with the exact same wording. */
    fun withNote(note: DishNote): UserData = copy(
        dishNotes = (
            dishNotes.filterNot { it.id == note.id || (it.recipeId == note.recipeId && it.text == note.text) } +
                note
            ).takeLast(NOTE_LIMIT),
    )

    fun withoutNote(id: String): UserData = copy(dishNotes = dishNotes.filterNot { it.id == id })

    companion object {
        /** Enough history for one kitchen; keeps the file small. */
        const val NOTE_LIMIT = 200
    }
}
