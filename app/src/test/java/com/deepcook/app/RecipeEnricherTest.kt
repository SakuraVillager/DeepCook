package com.deepcook.app

import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeStep
import com.deepcook.app.data.ai.RecipeEnricher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeEnricherTest {
    private fun recipe(vararg amounts: String) = Recipe(
        id = "test-dish",
        title = "红烧肉",
        description = "",
        categories = listOf("家常菜"),
        servings = "无",
        ingredients = amounts.mapIndexed { index, amount -> Ingredient("i$index", "材料$index", amount) },
        steps = listOf(RecipeStep("s1", 1, "上色", "炒糖色。")),
    )

    @Test fun onlyMissingFieldsAreAskedFor() {
        val enricher = RecipeEnricher(FakeGateway())
        val missing = enricher.missingFields(recipe("500 克", "适量"))
        assertTrue(missing.any { it.contains("用时") })
        assertTrue(missing.any { it.contains("难度") })
        assertTrue(missing.any { it.contains("份量") })
        assertTrue(missing.any { it.contains("i1") })
        assertTrue(missing.none { it.contains("i0") })
    }

    @Test fun aCompleteRecipeNeedsNoCall() {
        val complete = Recipe(
            id = "complete",
            title = "番茄炒蛋",
            description = "",
            categories = listOf("家常菜"),
            minutes = 15,
            difficulty = 1,
            servings = "2 人份",
            ingredients = listOf(Ingredient("i0", "番茄", "2 个")),
            steps = listOf(RecipeStep("s1", 1, "炒", "炒熟。")),
        )
        assertTrue(RecipeEnricher(FakeGateway()).missingFields(complete).isEmpty())
    }

    @Test fun aValidAnswerIsKeptAndRangeChecked() {
        val raw = """{"minutes": 45, "difficulty": 3, "servings": "2 人份", "amounts": {"i1": "3 克"}}"""
        val overlay = RecipeEnricher.parse(recipe("500 克", "适量"), raw, 123)!!
        assertEquals(45, overlay.minutes)
        assertEquals(3, overlay.difficulty)
        assertEquals("2 人份", overlay.servings)
        assertEquals(mapOf("i1" to "3 克"), overlay.amounts)
        assertEquals(123, overlay.fetchedAt)
    }

    @Test fun outOfRangeAndUnknownValuesAreDropped() {
        val raw = """{"minutes": 999, "difficulty": 9, "servings": "无", "amounts": {"i9": "3 克", "i1": "少许"}}"""
        // Nothing valid is left, so no overlay is stored at all.
        assertNull(RecipeEnricher.parse(recipe("500 克", "适量"), raw, 1))
    }

    @Test fun aConcreteAmountForAPlaceholderIsKept() {
        val raw = """{"amounts": {"i1": "3 克"}}"""
        val overlay = RecipeEnricher.parse(recipe("500 克", "适量"), raw, 1)!!
        assertEquals(mapOf("i1" to "3 克"), overlay.amounts)
    }

    @Test fun aFencedAnswerIsStillParsed() {
        val raw = "```json\n{\"minutes\": 30}\n```"
        assertEquals(30, RecipeEnricher.parse(recipe("500 克"), raw, 1)?.minutes)
    }

    @Test fun proseOrEmptyJsonYieldsNothing() {
        assertNull(RecipeEnricher.parse(recipe("500 克"), "这道菜大约 30 分钟。", 1))
        assertNull(RecipeEnricher.parse(recipe("500 克"), "{}", 1))
    }

    private class FakeGateway : com.deepcook.app.data.ai.AiGateway {
        override fun stream(messages: List<com.deepcook.app.data.ai.AiMessage>) =
            kotlinx.coroutines.flow.emptyFlow<com.deepcook.app.data.ai.AiEvent>()

        override suspend fun complete(messages: List<com.deepcook.app.data.ai.AiMessage>): String? = null
    }
}
