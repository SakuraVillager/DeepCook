package com.deepcook.app

import com.deepcook.app.core.model.AiOverlay
import com.deepcook.app.core.model.DishNote
import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.NoteSource
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeStep
import com.deepcook.app.core.model.UserData
import com.deepcook.app.core.model.UserProfile
import com.deepcook.app.data.ai.DishContextBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DishContextBuilderTest {
    private val recipe = Recipe(
        id = "test-dish",
        region = "中式",
        title = "红烧肉",
        description = "测试用",
        categories = listOf("家常菜"),
        minutes = 60,
        difficulty = 3,
        servings = "4 人份",
        ingredients = listOf(Ingredient("i0", "五花肉", "500 克"), Ingredient("i1", "冰糖", "30 克")),
        preparation = listOf("五花肉切麻将块"),
        steps = listOf(
            RecipeStep("s1", 1, "焯水", "冷水下锅焯水三分钟。"),
            RecipeStep("s2", 2, "收汁", "大火收汁到挂勺。", cue = "汤汁发亮"),
        ),
        notes = listOf("糖色别炒过头"),
    )

    private val user = UserData(
        profile = UserProfile(displayName = "小明", likes = setOf("辣"), avoids = setOf("香菜")),
        dishNotes = listOf(
            DishNote(
                id = "n1",
                recipeId = "test-dish",
                text = "少放盐",
                updatedAt = 1,
                source = NoteSource.USER,
            ),
        ),
    )
    private val builder = DishContextBuilder()

    @Test fun theDishFactsCarryIngredientsWarningsAndThisDishesNotes() {
        val messages = builder.messages(recipe, user, "我做的为什么发苦")
        assertEquals("system", messages.first().role)
        val system = messages.first().content
        assertTrue(system.contains("红烧肉"))
        assertTrue(system.contains("五花肉 500 克"))
        assertTrue(system.contains("冰糖 30 克"))
        assertTrue(system.contains("糖色别炒过头"))
        assertTrue(system.contains("少放盐"))
        assertTrue(system.contains("忌口：香菜"))
        assertTrue(system.contains("1. 焯水"))
        assertEquals("user", messages.last().role)
        assertEquals("我做的为什么发苦", messages.last().content)
    }

    @Test fun onlyStepTitlesAreSentNotTheWholeProse() {
        val system = builder.messages(recipe, user, "没有蚝油用什么").first().content
        assertFalse(system.contains("冷水下锅焯水三分钟"))
        assertFalse(system.contains("大火收汁到挂勺"))
    }

    @Test fun aFilledAmountWinsOverThePrintedPlaceholder() {
        val overlay = AiOverlay(recipeId = recipe.id, servings = "2 人份", amounts = mapOf("i1" to "10 克"))
        val system = builder.messages(recipe, user, "没有冰糖用什么", overlay).first().content
        assertTrue(system.contains("冰糖 10 克"))
        assertTrue(system.contains("2 人份"))
    }
}
