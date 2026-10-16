package com.deepcook.app

import com.deepcook.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class RecipeFilterTest {
    private fun recipe(
        id: String,
        title: String,
        minutes: Int?,
        difficulty: Int?,
        categories: List<String>,
        ingredient: String,
    ) = Recipe(
        id = id, title = title, description = "", minutes = minutes, difficulty = difficulty,
        categories = categories, servings = "2 人份", ingredients = listOf(Ingredient("i1", ingredient, "100 克")),
        steps = listOf(RecipeStep("$id-step", 1, "准备", "准备材料")),
    )
    private val recipes = listOf(
        recipe("a", "宫保鸡丁", 30, 3, listOf("家常菜", "肉类"), "鸡腿肉"),
        recipe("b", "滑蛋", 10, 1, listOf("快手菜"), "鸡蛋"),
        recipe("c", "豆腐汤", null, null, listOf("素食", "汤羹"), "嫩豆腐"),
    )

    @Test fun searchesNameAndIngredientWithAllKeywords() {
        assertEquals(listOf("a"), recipes.filteredBy(RecipeFilter(query = "  宫保   鸡腿肉  ")).map { it.id })
        assertEquals(listOf("b"), recipes.filteredBy(RecipeFilter(query = "鸡蛋")).map { it.id })
        assertTrue(recipes.filteredBy(RecipeFilter(query = "宫保 豆腐")).isEmpty())
    }

    @Test fun combinesFacetsAndPreservesCatalogOrder() {
        assertEquals(
            listOf("a"),
            recipes.filteredBy(RecipeFilter(category = "肉类", time = TimeFilter.THIRTY, difficulty = 3)).map {
                it.id
            },
        )
        assertEquals(listOf("a", "b", "c"), recipes.filteredBy(RecipeFilter()).map { it.id })
        assertTrue(recipes.filteredBy(RecipeFilter(category = "素食", difficulty = 1)).isEmpty())
    }

    @Test fun missingMetadataOnlyMatchesUnrestrictedFacet() {
        assertFalse(TimeFilter.THIRTY.matches(null))
        assertTrue(TimeFilter.ANY.matches(null))
        assertEquals(listOf("b"), recipes.filteredBy(RecipeFilter(time = TimeFilter.FIFTEEN)).map { it.id })
        assertFalse(TimeFilter.LONG.matches(60))
        assertTrue(TimeFilter.LONG.matches(61))
    }

    @Test fun allFiveScoresFilterExactlyAndExcludeUnratedRecipes() {
        val rated =
            (1..5).map { score -> recipes.first().copy(id = "score-$score", difficulty = score) } + recipes.last()
        for (score in 1..5) {
            assertEquals(listOf("score-$score"), rated.filteredBy(RecipeFilter(difficulty = score)).map { it.id })
        }
        assertEquals(6, rated.filteredBy(RecipeFilter()).size)
    }

    @Test fun contentRejectsOutOfRangeDifficultyScores() {
        assertThrows(IllegalArgumentException::class.java) { recipes.first().copy(difficulty = 0) }
        assertThrows(IllegalArgumentException::class.java) { recipes.first().copy(difficulty = 6) }
    }
}
