package com.deepcook.app

import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeStep
import com.deepcook.app.core.model.SavedRecipe
import com.deepcook.app.core.model.UserData
import com.deepcook.app.core.model.UserProfile
import com.deepcook.app.data.ai.AiExchange
import com.deepcook.app.data.ai.ContextBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTest {
    private val recipe = Recipe(
        id = "test-dish",
        region = "中式",
        title = "红烧肉",
        description = "测试用",
        categories = listOf("家常菜"),
        minutes = 60,
        difficulty = 3,
        servings = "2 人份",
        ingredients = listOf(Ingredient("i0", "五花肉", "500 克"), Ingredient("i1", "冰糖", "30 克")),
        steps = listOf(
            RecipeStep("s1", 1, "焯水", "五花肉冷水下锅焯水。", ingredientRefs = listOf("i0")),
            RecipeStep("s2", 2, "收汁", "大火收汁到挂勺。", cue = "汤汁发亮"),
        ),
    )

    private val user = UserData(
        favorites = listOf(SavedRecipe("other", 1)),
        recent = listOf(SavedRecipe("other", 2)),
        profile = UserProfile(displayName = "小明", likes = setOf("辣"), avoids = setOf("香菜")),
    )

    private val titles = mapOf("other" to "番茄炒蛋")
    private val builder = ContextBuilder()

    @Test fun theSystemMessageCarriesTheCurrentStepAndTheUsersAvoids() {
        val messages = builder.messages(recipe, 1, user, titles, "汁收多久", emptyList())
        assertEquals("system", messages.first().role)
        val system = messages.first().content
        assertTrue(system.contains("第 2/2 步"))
        assertTrue(system.contains("大火收汁到挂勺"))
        assertTrue(system.contains("忌口：香菜"))
        assertTrue(system.contains("番茄炒蛋"))
        assertEquals("user", messages.last().role)
        assertEquals("汁收多久", messages.last().content)
    }

    @Test fun onlyTheLastTwoExchangesAreReplayed() {
        val history = listOf(
            AiExchange("第一问", "第一答"),
            AiExchange("第二问", "第二答"),
            AiExchange("第三问", "第三答"),
        )
        val messages = builder.messages(recipe, 0, user, titles, "新问题", history)
        assertEquals(1 + 4 + 1, messages.size)
        assertTrue(messages.none { it.content == "第一问" })
        assertTrue(messages.any { it.content == "第二问" })
        assertTrue(messages.any { it.content == "第三答" })
    }

    @Test fun anEmptyKitchenStillProducesAFactsBlock() {
        val messages = builder.messages(recipe, 0, UserData(), emptyMap(), "怎么做", emptyList())
        val system = messages.first().content
        assertTrue(system.contains("喜好：未填"))
        assertTrue(system.contains("忌口：未填"))
    }

    @Test fun enrichmentValuesTakePrecedenceInTheFactsBlock() {
        val overlay = com.deepcook.app.core.model.AiOverlay(
            recipeId = recipe.id,
            minutes = 45,
            difficulty = 4,
            servings = "3 人份",
            amounts = mapOf("i1" to "10 克"),
        )
        val messages = builder.messages(recipe, 0, user, titles, "要多久", emptyList(), overlay)
        val system = messages.first().content
        assertTrue(system.contains("用时 45 分钟"))
        assertTrue(system.contains("难度 4/5"))
        assertTrue(system.contains("3 人份"))
        assertTrue(system.contains("冰糖 10 克"))
    }
}
