package com.deepcook.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.deepcook.app.core.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

/**
 * Everything the user authored on this device, in its own file.
 *
 * It is deliberately separate from `assets/recipes.json`: user content changes constantly,
 * must never be overwritten by an app update, and must not be able to damage the bundled
 * catalog if its file ever becomes unreadable.
 */
@Serializable
data class UserRecipeBook(val recipes: List<Recipe> = emptyList()) {
    /** Replaces the entry with the same id in place, or appends a new one, keeping order. */
    fun withRecipe(recipe: Recipe): UserRecipeBook {
        val index = recipes.indexOfFirst { it.id == recipe.id }
        return if (index < 0) {
            copy(recipes = recipes + recipe)
        } else {
            copy(recipes = recipes.toMutableList().also { list -> list[index] = recipe })
        }
    }

    fun without(id: String): UserRecipeBook = copy(recipes = recipes.filterNot { it.id == id })
}

object UserRecipeBookSerializer : Serializer<UserRecipeBook> {
    override val defaultValue = UserRecipeBook()

    override suspend fun readFrom(input: InputStream): UserRecipeBook = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (error: SerializationException) {
        // Keep the file for inspection instead of replacing what the user wrote.
        throw CorruptionException("我的菜谱无法读取", error)
    } catch (error: IllegalArgumentException) {
        // Recipe validates in its constructor, so a hand-edited or older file can fail here.
        throw CorruptionException("我的菜谱内容不合法", error)
    }

    override suspend fun writeTo(t: UserRecipeBook, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

interface UserRecipeStore {
    /** Creation order, oldest first. */
    val recipes: Flow<List<Recipe>>
    suspend fun save(recipe: Recipe)
    suspend fun delete(id: String)
}

class LocalUserRecipeStore(private val store: DataStore<UserRecipeBook>) : UserRecipeStore {
    override val recipes: Flow<List<Recipe>> = store.data.map { it.recipes }

    override suspend fun save(recipe: Recipe) {
        store.updateData { it.withRecipe(recipe) }
    }

    override suspend fun delete(id: String) {
        store.updateData { it.without(id) }
    }
}
