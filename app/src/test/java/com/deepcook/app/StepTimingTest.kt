package com.deepcook.app

import com.deepcook.app.core.model.DraftIngredient
import com.deepcook.app.core.model.DraftStep
import com.deepcook.app.core.model.RecipeDraft
import com.deepcook.app.core.model.RecipeStep
import com.deepcook.app.core.model.StepKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step timing is optional content, so old files must load and edits must not drop it. */
class StepTimingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun stepsWrittenBeforeTimingExistedStillLoad() {
        val step = json.decodeFromString<RecipeStep>("""{"id":"s1","order":1,"title":"切","text":"切块。"}""")
        assertNull(step.minutes)
        assertNull(step.kind)
    }

    @Test fun timingSurvivesSerialization() {
        val step = RecipeStep("s1", 1, "炖", "小火炖四十分钟。", minutes = 40, kind = StepKind.PASSIVE)
        val back = json.decodeFromString<RecipeStep>(json.encodeToString(step))
        assertEquals(40, back.minutes)
        assertEquals(StepKind.PASSIVE, back.kind)
    }

    @Test fun editingKeepsTheTimingData() {
        val recipe = RecipeDraft(
            title = "炖肉",
            region = "中式",
            categories = listOf("家常菜"),
            ingredients = listOf(DraftIngredient("五花肉", "500 克")),
            steps = listOf(DraftStep("炖", "小火炖四十分钟。", minutes = 40, kind = StepKind.PASSIVE)),
        ).toRecipe("user-abc123")
        val reopened = RecipeDraft.from(recipe)
        assertEquals(40, reopened.steps.single().minutes)
        assertEquals(StepKind.PASSIVE, reopened.steps.single().kind)
        val saved = reopened.toRecipe("user-abc123")
        assertEquals(40, saved.steps.single().minutes)
        assertEquals(StepKind.PASSIVE, saved.steps.single().kind)
    }

    @Test fun anImpossibleDurationIsRejectedByTheEditor() {
        val draft = RecipeDraft(
            title = "炖肉",
            region = "中式",
            categories = listOf("家常菜"),
            ingredients = listOf(DraftIngredient("五花肉", "500 克")),
            steps = listOf(DraftStep("炖", "炖四十分钟。", minutes = 999)),
        )
        assertTrue(draft.problems().any { it.contains("时长") })
    }
}
