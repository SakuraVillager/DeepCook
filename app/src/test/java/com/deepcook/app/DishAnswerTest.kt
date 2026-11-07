package com.deepcook.app

import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeStep
import com.deepcook.app.data.ai.DishAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DishAnswerTest {
    private fun recipe(notes: List<String> = emptyList()) = Recipe(
        id = "test-dish",
        title = "红烧肉",
        description = "",
        categories = listOf("家常菜"),
        servings = "4 人份",
        ingredients = listOf(Ingredient("i0", "五花肉", "500 克")),
        steps = listOf(
            RecipeStep("s1", 1, "焯水", "冷水下锅。"),
            RecipeStep("s2", 2, "收汁", "大火收汁。"),
        ),
        notes = notes,
    )

    @Test fun anAnswerIsCutToTwoSentences() {
        assertEquals("第一句。第二句。", DishAnswer.twoSentences("第一句。第二句。第三句。"))
        assertEquals("第一句！第二句？", DishAnswer.twoSentences("第一句！第二句？第三句"))
        assertEquals("第一行\n第二行", DishAnswer.twoSentences("第一行\n第二行\n第三行"))
    }

    @Test fun aShortAnswerIsLeftAlone() {
        assertEquals("先焯水再炒。", DishAnswer.twoSentences(" 先焯水再炒。 "))
        assertEquals("", DishAnswer.twoSentences("   "))
    }

    @Test fun theFallbackUsesTheRecipesOwnWords() {
        val withNotes = DishAnswer.recipeFallback(recipe(notes = listOf("糖色别炒过头")))
        assertTrue(withNotes.contains("焯水"))
        assertTrue(withNotes.contains("糖色别炒过头"))
        assertTrue(DishAnswer.twoSentences(withNotes) == withNotes)
    }

    @Test fun theMissingIngredientHintNamesThePrintedAmount() {
        val known = DishAnswer.missingIngredientHint("蚝油", "1 汤匙")
        assertTrue(known.contains("1 汤匙"))
        assertTrue(known.contains("蚝油"))
        val placeholder = DishAnswer.missingIngredientHint("蚝油", "适量")
        assertTrue(placeholder.contains("适量"))
    }
}
