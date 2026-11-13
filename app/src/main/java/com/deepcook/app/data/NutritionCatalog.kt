package com.deepcook.app.data

import com.deepcook.app.core.model.Nutrition
import com.deepcook.app.core.model.Recipe
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class NutritionEntry(val recipeKey: List<String>, val values: Nutrition)

@Serializable
private data class NutritionFile(val schema: Int, val recipes: Map<String, NutritionEntry>)

class NutritionCatalog private constructor(private val data: NutritionFile) {
    fun estimate(recipe: Recipe): Nutrition? {
        val entry = data.recipes[recipe.id] ?: return null
        val key = listOf(recipe.servings) + recipe.ingredients.flatMap { listOf(it.name, it.amount) }
        return entry.values.takeIf { key == entry.recipeKey }
    }

    companion object {
        fun decode(raw: String): NutritionCatalog {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<NutritionFile>(raw)
            require(data.schema == 1) { "营养资料版本不匹配" }
            return NutritionCatalog(data)
        }
    }
}
