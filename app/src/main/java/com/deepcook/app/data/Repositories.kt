package com.deepcook.app.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeFilter
import com.deepcook.app.core.model.UserData
import com.deepcook.app.core.model.UserPreferences
import com.deepcook.app.core.model.UserProfile
import com.deepcook.app.core.model.filteredBy
import com.deepcook.app.data.lookup.CatalogFirstLookup
import com.deepcook.app.data.lookup.HowToCookLookup
import com.deepcook.app.data.lookup.RecipeLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

internal val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

interface RecipeRepository {
    suspend fun recipes(): List<Recipe>
    suspend fun recipe(id: String): Recipe? = recipes().find { it.id == id }
    suspend fun search(filter: RecipeFilter): List<Recipe> = recipes().filteredBy(filter)
}

class AssetRecipeRepository(private val context: Context) : RecipeRepository {
    override suspend fun recipes(): List<Recipe> = withContext(Dispatchers.IO) {
        context.assets.open("recipes.json").bufferedReader().use {
            json.decodeFromString<List<Recipe>>(it.readText())
        }.also { recipes ->
            require(recipes.map { it.id }.distinct().size == recipes.size) { "菜谱 ID 重复" }
            recipes.forEach { recipe ->
                require(recipe.steps.isNotEmpty()) { "菜谱缺少步骤" }
                require(recipe.steps.map { it.id }.distinct().size == recipe.steps.size) { "步骤 ID 重复" }
            }
        }
    }
}

interface UserDataRepository {
    val data: Flow<UserData>
    suspend fun setFavorite(id: String, favorite: Boolean, timestamp: Long = System.currentTimeMillis())
    suspend fun recordVisit(id: String)
    suspend fun removeVisit(id: String)
    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences)
    suspend fun updateProfile(transform: (UserProfile) -> UserProfile)
    suspend fun clearRecent()
    suspend fun clearFavorites()
    suspend fun resetPreferences()

    /** Drops [id] from both collections; used when a user recipe is deleted. */
    suspend fun forget(id: String)
}

object UserDataSerializer : Serializer<UserData> {
    override val defaultValue = UserData()
    override suspend fun readFrom(input: InputStream): UserData = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (error: SerializationException) {
        // Preserve the unreadable file; never silently replace the user's collections.
        throw CorruptionException("本地数据无法读取", error)
    }

    override suspend fun writeTo(t: UserData, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

class LocalUserDataRepository(private val store: DataStore<UserData>) : UserDataRepository {
    override val data = store.data
    override suspend fun setFavorite(id: String, favorite: Boolean, timestamp: Long) {
        store.updateData { it.withFavorite(id, favorite, timestamp) }
    }
    override suspend fun recordVisit(id: String) {
        store.updateData { it.withVisit(id, System.currentTimeMillis()) }
    }
    override suspend fun removeVisit(id: String) {
        store.updateData {
            it.copy(
                recent = it.recent.filterNot { r ->
                    r.recipeId ==
                        id
                },
            )
        }
    }
    override suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        store.updateData { it.copy(preferences = transform(it.preferences)) }
    }
    override suspend fun updateProfile(transform: (UserProfile) -> UserProfile) {
        // Sanitised on the way in: a stored tag this build no longer offers is dropped only when
        // the profile is written, so reading an older file never loses data silently.
        store.updateData { it.copy(profile = transform(it.profile).sanitized()) }
    }
    override suspend fun clearRecent() {
        store.updateData { it.copy(recent = emptyList()) }
    }
    override suspend fun clearFavorites() {
        store.updateData { it.copy(favorites = emptyList()) }
    }
    override suspend fun resetPreferences() {
        store.updateData { it.copy(preferences = UserPreferences()) }
    }

    override suspend fun forget(id: String) {
        store.updateData {
            it.copy(
                favorites = it.favorites.filterNot { saved -> saved.recipeId == id },
                recent = it.recent.filterNot { saved -> saved.recipeId == id },
            )
        }
    }
}

class AppContainer(context: Context) {
    val recipes: RecipeRepository = AssetRecipeRepository(context)
    val userData: UserDataRepository = LocalUserDataRepository(
        DataStoreFactory.create(serializer = UserDataSerializer) { context.dataStoreFile("user_data.json") },
    )
    val userRecipes: UserRecipeStore = LocalUserRecipeStore(
        DataStoreFactory.create(serializer = UserRecipeBookSerializer) {
            context.dataStoreFile("user_recipes.json")
        },
    )
    val lookup: RecipeLookup = CatalogFirstLookup(recipes, HowToCookLookup(context))
    val covers = CoverStore(context)
    val avatars = AvatarStore(context)
}
