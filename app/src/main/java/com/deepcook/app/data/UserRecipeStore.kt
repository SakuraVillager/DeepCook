package com.deepcook.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.isMine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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
    fun validate(): UserRecipeBook = apply {
        require(recipes.map { it.id }.distinct().size == recipes.size) { "我的菜谱 ID 重复" }
        recipes.forEach { recipe ->
            require(recipe.isMine && recipe.title.isNotBlank()) { "我的菜谱标识不合法" }
            require(recipe.ingredients.isNotEmpty() && recipe.ingredients.all { it.name.isNotBlank() }) { "材料不能为空" }
            require(recipe.steps.isNotEmpty() && recipe.steps.all { it.text.isNotBlank() }) { "步骤正文不能为空" }
            require(recipe.steps.map { it.id }.distinct().size == recipe.steps.size) { "步骤 ID 重复" }
            require(recipe.ingredients.map { it.id }.distinct().size == recipe.ingredients.size) { "材料 ID 重复" }
        }
    }

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
        json.decodeFromString<UserRecipeBook>(input.readBytes().decodeToString()).validate()
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
        store.updateData { it.withRecipe(recipe).validate() }
    }

    override suspend fun delete(id: String) {
        store.updateData { it.without(id) }
    }
}
