package com.deepcook.app.data.lookup

import com.deepcook.app.core.model.RecipeDraft
import com.deepcook.app.data.RecipeRepository

/** Reuse reviewed bundled content offline; fetch only when it is absent locally. */
class CatalogFirstLookup(private val recipes: RecipeRepository, private val fallback: RecipeLookup) : RecipeLookup {
    override suspend fun lookup(name: String): LookupOutcome {
        val catalog = recipes.recipes()
        val names = catalog.flatMap { recipe ->
            (listOf(recipe.title) + recipe.aliases).map { DishEntry(it, recipe.id, recipe.collection) }
        }
        val matched = DishMatcher.match(name, names) ?: return fallback.lookup(name)
        val recipe = catalog.first { it.id == matched.path }
        return LookupOutcome.Filled(
            RecipeDraft.from(recipe).copy(version = 0),
            recipe.title,
            recipe.source.url.orEmpty(),
            recipe.collection.ifBlank { recipe.source.author },
        )
    }
}
