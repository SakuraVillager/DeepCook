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
        region: String = "中式",
        preparation: List<String> = emptyList(),
        notes: List<String> = emptyList(),
        cue: String? = null,
    ) = Recipe(
        id = id, version = 3, region = region, title = title, description = "", minutes = minutes,
        difficulty = difficulty, categories = categories, servings = "原始份量 · 2 人份",
        ingredients = listOf(Ingredient("i0", ingredient, "100 克")),
        preparation = preparation,
        steps = listOf(RecipeStep("$id-s1", 1, "准备", "准备材料", cue = cue)),
        notes = notes,
    )

    private val recipes = listOf(
        recipe("a", "宫保鸡丁", 30, 3, listOf("家常菜", "肉类"), "鸡腿肉"),
        recipe("b", "滑蛋", 10, 1, listOf("快手菜"), "鸡蛋"),
        recipe("c", "豆腐汤", null, null, listOf("素食", "汤羹"), "嫩豆腐"),
    )

    private val cuisines = listOf(
        recipe("cn-1", "鱼香肉丝", 30, 3, listOf("家常菜", "肉类"), "猪里脊", region = "中式"),
        recipe("kr-1", "石锅拌饭", 35, 3, listOf("主食", "肉类"), "米饭", region = "韩式"),
        recipe("jp-1", "亲子丼", 25, 2, listOf("主食", "肉类"), "鸡腿肉", region = "日式"),
        recipe("us-1", "经典汉堡", 30, 2, listOf("主食", "肉类"), "牛肉末", region = "美式"),
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

    @Test fun regionFacetSelectsCuisineAndKeepsDishStylesSeparate() {
        assertEquals(listOf("kr-1"), cuisines.filteredBy(RecipeFilter(category = "韩式")).map { it.id })
        assertEquals(
            listOf("cn-1", "kr-1", "jp-1", "us-1"),
            cuisines.filteredBy(RecipeFilter(category = "全部")).map { it.id },
        )
        assertEquals(
            listOf("kr-1", "jp-1", "us-1"),
            cuisines.filteredBy(RecipeFilter(category = "主食")).map { it.id },
        )
        assertTrue(cuisines.filteredBy(RecipeFilter(category = "泰式")).isEmpty())
    }

    @Test fun regionNameIsSearchableFromTheTextField() {
        assertEquals(listOf("kr-1"), cuisines.filteredBy(RecipeFilter(query = "韩式")).map { it.id })
        assertEquals(listOf("cn-1"), cuisines.filteredBy(RecipeFilter(query = "中式")).map { it.id })
    }

    @Test fun categoryPickerListsRegionsThenDishStyles() {
        assertEquals("全部", RecipeCategoryOptions.first())
        assertEquals(RecipeRegions + RecipeCategories, RecipeCategoryOptions.drop(1))
        assertEquals(RecipeCategoryOptions.size, RecipeCategoryOptions.distinct().size)
    }

    @Test fun placeholderEntriesAreHiddenInsteadOfRendered() {
        val plain = recipe(
            "plain",
            "清汤面",
            15,
            1,
            listOf("面食"),
            "面条",
            preparation = listOf(NO_CONTENT),
            notes = listOf(NO_CONTENT),
            cue = NO_CONTENT,
        )
        assertTrue(plain.preparationItems.isEmpty())
        assertTrue(plain.noteItems.isEmpty())
        assertNull(plain.steps.single().visibleCue)

        val filled = plain.copy(
            preparation = listOf("切好葱花"),
            notes = listOf("含麸质。"),
            steps = listOf(plain.steps.single().copy(cue = "面条断生")),
        )
        assertEquals(listOf("切好葱花"), filled.preparationItems)
        assertEquals(listOf("含麸质。"), filled.noteItems)
        assertEquals("面条断生", filled.steps.single().visibleCue)
    }

    @Test fun regionAndCategoriesMustBeRegistered() {
        assertThrows(IllegalArgumentException::class.java) { recipes.first().copy(region = "火星") }
        assertThrows(IllegalArgumentException::class.java) { recipes.first().copy(categories = listOf("未登记分类")) }
        assertThrows(IllegalArgumentException::class.java) { recipes.first().copy(categories = emptyList()) }
    }
}
