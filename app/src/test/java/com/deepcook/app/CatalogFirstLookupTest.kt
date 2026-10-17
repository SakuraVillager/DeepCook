package com.deepcook.app

import com.deepcook.app.core.model.*
import com.deepcook.app.data.RecipeRepository
import com.deepcook.app.data.lookup.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CatalogFirstLookupTest {
    private val recipe = Recipe(
        id = "world-miso", title = "日式味噌汤", aliases = listOf("Miso Soup"), description = "中文说明",
        categories = listOf("汤羹", "国际菜", "日本风味"), region = "日式", servings = "无",
        ingredients = listOf(Ingredient("i0", "味噌", "无")),
        steps = listOf(RecipeStep("s1", 1, "化开", "将味噌化入汤中。")),
        collection = "Based Cooking", source = RecipeSource("原作者", "Unlicense · 中文译文", "https://example.invalid/miso"),
    )

    @Test fun bundledAliasWorksOfflineAndPreservesSourceAndUnknownValues() = runTest {
        val repo = object : RecipeRepository {
            override suspend fun recipes() = listOf(recipe)
        }
        val forbidden = object : RecipeLookup {
            override suspend fun lookup(name: String): LookupOutcome = error("Bundled content must not use network")
        }
        val outcome = CatalogFirstLookup(repo, forbidden).lookup("miso soup") as LookupOutcome.Filled
        assertEquals(recipe.title, outcome.matchedName)
        assertNull(outcome.draft.minutes)
        assertNull(outcome.draft.difficulty)
        assertTrue(outcome.draft.servings.isBlank())
        val saved = outcome.draft.toRecipe("user-test")
        assertEquals(recipe.source.author, saved.source.author)
        assertEquals(recipe.source.url, saved.source.url)
        assertTrue(saved.source.license.contains("中文译文"))
        assertTrue("日本风味" in saved.categories)
    }

    @Test fun missingLocalDishUsesFallbackWithoutBlockingManualInput() = runTest {
        val repo = object : RecipeRepository {
            override suspend fun recipes() = emptyList<Recipe>()
        }
        val fallback = object : RecipeLookup {
            override suspend fun lookup(name: String) = LookupOutcome.Offline
        }
        assertEquals(LookupOutcome.Offline, CatalogFirstLookup(repo, fallback).lookup("未知菜"))
    }
}
