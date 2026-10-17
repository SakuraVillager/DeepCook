package com.deepcook.app

import com.deepcook.app.core.model.DraftIngredient
import com.deepcook.app.core.model.DraftStep
import com.deepcook.app.core.model.NO_CONTENT
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeDraft
import com.deepcook.app.core.model.isMine
import org.junit.Assert.*
import org.junit.Test

class RecipeDraftTest {
    private val complete = RecipeDraft(
        title = "  我的蛋炒饭  ",
        region = "中式",
        categories = listOf("主食", "快手菜"),
        minutes = 15,
        difficulty = 2,
        servings = " 2 人份 ",
        description = "剩饭的好去处。",
        preparation = listOf("米饭提前放凉", "  "),
        ingredients = listOf(
            DraftIngredient("米饭", "2 碗"),
            DraftIngredient("鸡蛋", "2 个"),
            DraftIngredient("", ""),
        ),
        steps = listOf(
            DraftStep("打蛋", "把鸡蛋打散。"),
            DraftStep("炒饭", "下米饭翻炒。", "米粒跳起"),
            DraftStep("", ""),
        ),
        notes = listOf("火不要太大。"),
    )

    @Test fun anEmptyDraftExplainsWhatIsMissing() {
        val problems = RecipeDraft().problems()
        assertTrue(problems.any { it.contains("菜名") })
        assertTrue(problems.any { it.contains("分类") })
        assertTrue(problems.any { it.contains("材料") })
        assertTrue(problems.any { it.contains("步骤") })
    }

    @Test fun aCompleteDraftHasNoProblems() {
        assertEquals(emptyList<String>(), complete.problems())
    }

    @Test fun outOfRangeValuesAreReported() {
        assertTrue(complete.copy(minutes = 0).problems().any { it.contains("用时") })
        assertTrue(complete.copy(minutes = 5000).problems().any { it.contains("用时") })
        assertTrue(complete.copy(difficulty = 6).problems().any { it.contains("难度") })
        assertTrue(complete.copy(region = "火星").problems().any { it.contains("地域") })
        assertTrue(complete.copy(categories = listOf("不存在的分类")).problems().any { it.contains("分类") })
    }

    @Test fun blankRowsAreDroppedAndIdsAreDerivedFromTheRecipeId() {
        val recipe = complete.toRecipe("user-abc123")
        assertEquals("user-abc123", recipe.id)
        assertTrue(recipe.isMine)
        assertEquals(listOf("米饭", "鸡蛋"), recipe.ingredients.map { it.name })
        assertEquals(listOf("i0", "i1"), recipe.ingredients.map { it.id })
        assertEquals(listOf("user-abc123-s1", "user-abc123-s2"), recipe.steps.map { it.id })
        assertEquals(listOf(1, 2), recipe.steps.map { it.order })
    }

    @Test fun textIsTrimmedAndEmptyCuesBecomeTheHiddenPlaceholder() {
        val recipe = complete.toRecipe("user-abc123")
        assertEquals("我的蛋炒饭", recipe.title)
        assertEquals("2 人份", recipe.servings)
        // The blank preparation entry is not rendered as an empty row.
        assertEquals(listOf("米饭提前放凉"), recipe.preparationItems)
        assertEquals(NO_CONTENT, recipe.steps.first().cue!!)
        assertNull(recipe.steps.first().visibleCue)
        assertEquals("米粒跳起", recipe.steps[1].visibleCue)
    }

    @Test fun aPrefilledRecipeKeepsItsAttribution() {
        val prefilled = complete.copy(
            prefilled = true,
            sourceName = "HowToCook",
            sourceUrl = "https://example.invalid/蛋炒饭.md",
        ).toRecipe("user-abc123")
        assertEquals("https://example.invalid/蛋炒饭.md", prefilled.source.url)
        assertTrue(prefilled.source.license.contains("HowToCook"))
        assertTrue(prefilled.source.license.contains("Unlicense"))
    }

    @Test fun anIncompleteDraftCannotBecomeARecipe() {
        try {
            RecipeDraft().toRecipe("user-abc123")
            fail("An empty draft must not produce a recipe")
        } catch (_: IllegalArgumentException) {
            // Expected: the editor shows problems() instead of saving.
        }
    }

    @Test fun editingLoadsTheRecipeBackIntoTheForm() {
        val recipe = complete.toRecipe("user-abc123")
        val reopened = RecipeDraft.from(recipe)
        assertEquals(recipe.title, reopened.title)
        assertEquals(recipe.region, reopened.region)
        assertEquals(recipe.categories, reopened.categories)
        assertEquals(recipe.minutes, reopened.minutes)
        assertEquals(recipe.difficulty, reopened.difficulty)
        assertEquals(recipe.ingredients.map { it.name }, reopened.ingredients.map { it.name })
        assertEquals(recipe.steps.map { it.text }, reopened.steps.map { it.text })
        // Round-tripping a saved recipe must still be saveable.
        assertEquals(emptyList<String>(), reopened.problems())
    }

    @Test fun untouchedOnlyTracksWhatTheUserTyped() {
        assertTrue(RecipeDraft().untouched)
        assertFalse(RecipeDraft(title = "蛋炒饭").untouched)
        assertFalse(complete.copy(title = "").untouched)
        assertFalse(RecipeDraft(cover = "file:/tmp/cover.jpg").untouched)
    }

    @Test fun aBundledRecipeIsNotMine() {
        val bundled = Recipe(
            id = "tomato-egg",
            title = "西红柿炒鸡蛋",
            description = "酸甜",
            categories = listOf("家常菜"),
            servings = "2 人份",
            ingredients = listOf(com.deepcook.app.core.model.Ingredient("i0", "鸡蛋", "2 个")),
            steps = listOf(com.deepcook.app.core.model.RecipeStep("tomato-egg-s1", 1, "打蛋", "打散")),
        )
        assertFalse(bundled.isMine)
    }
}
