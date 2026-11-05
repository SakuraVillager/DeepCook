package com.deepcook.app

import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeStep
import com.deepcook.app.data.ai.SafetyRules
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyRulesTest {
    private fun recipe(vararg names: String) = Recipe(
        id = "test-dish",
        title = "番茄炒蛋",
        description = "测试用",
        categories = listOf("家常菜"),
        servings = "2 人份",
        ingredients = names.mapIndexed { index, name -> Ingredient("i$index", name, "1 份") },
        steps = listOf(RecipeStep("s1", 1, "下锅", "炒到全熟。")),
    )

    @Test fun aFoodSafetyQuestionIsAnsweredConservatively() {
        val answer = SafetyRules.answer("这肉熟了吗", recipe("猪肉"), emptySet())
        assertTrue(answer!!.startsWith(SafetyRules.NOT_RECOMMENDED))
        assertTrue(answer.contains("再加热"))
    }

    @Test fun anAllergenThatIsInTheRecipeBlocksTheDish() {
        val answer = SafetyRules.answer("我对鸡蛋过敏", recipe("番茄", "鸡蛋"), emptySet())!!
        assertTrue(answer.startsWith(SafetyRules.NOT_RECOMMENDED))
        assertTrue(answer.contains("鸡蛋"))
    }

    @Test fun anAllergenThatIsNotInTheRecipeSaysSoAndAsksTheUserToCheckAdditions() {
        val answer = SafetyRules.answer("我对花生过敏", recipe("番茄", "鸡蛋"), emptySet())!!
        assertTrue(answer.contains("没有"))
        assertTrue(answer.contains("花生"))
        assertTrue(answer.contains("自行核对"))
    }

    @Test fun theUsersOwnAvoidTagsAreScannedWhenTheQuestionNamesNone() {
        val answer = SafetyRules.answer("我忌口香菜", recipe("香菜", "豆腐"), setOf("香菜"))!!
        assertTrue(answer.startsWith(SafetyRules.NOT_RECOMMENDED))
        assertTrue(answer.contains("香菜"))
    }

    @Test fun withoutAnyAllergenTheIngredientListIsReadBack() {
        val answer = SafetyRules.answer("我过敏", recipe("番茄", "鸡蛋"), emptySet())!!
        assertTrue(answer.contains("番茄"))
        assertTrue(answer.contains("鸡蛋"))
    }

    @Test fun ordinaryCookingQuestionsAreLeftToTheModel() {
        assertNull(SafetyRules.answer("盐放多少", recipe("番茄"), emptySet()))
        assertNull(SafetyRules.answer("   ", recipe("番茄"), emptySet()))
    }
}
