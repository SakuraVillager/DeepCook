package com.deepcook.app

import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeFilter
import com.deepcook.app.core.model.filteredBy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RecipeCatalogTest {
    private val assets = File("src/main/assets")
    private val json = Json { ignoreUnknownKeys = true }
    private val recipes = json.decodeFromString<List<Recipe>>(File(assets, "recipes.json").readText())

    @Test fun bundledCatalogHasValidContentAndOfflineImages() {
        assertTrue(recipes.size >= 300)
        assertEquals(recipes.size, recipes.map { it.id }.distinct().size)
        recipes.forEach { recipe ->
            assertTrue(recipe.title.isNotBlank())
            assertTrue(recipe.ingredients.isNotEmpty())
            assertTrue(recipe.ingredients.all { it.name.isNotBlank() && it.amount.isNotBlank() })
            assertTrue(recipe.steps.isNotEmpty())
            assertEquals(recipe.steps.size, recipe.steps.map { it.id }.distinct().size)
            assertEquals((1..recipe.steps.size).toList(), recipe.steps.map { it.order })
            assertTrue(recipe.steps.all { it.text.isNotBlank() })
            assertTrue(recipe.minutes == null || recipe.minutes > 0)
            assertTrue(recipe.source.url?.startsWith("https://github.com/") == true)
            recipe.cover?.let { assertTrue("Missing image: $it", File(assets, it).isFile) }
        }
    }

    @Test fun internationalContentIsChineseAndUnknownMetadataRemainsAbsent() {
        val world = recipes.filter { it.id.startsWith("world-") }
        assertTrue(world.size >= 20)
        assertTrue(world.flatMap { it.categories }.distinct().size >= 15)
        val chinese = Regex("[\\u4e00-\\u9fff]")
        world.forEach { recipe ->
            assertTrue(chinese.containsMatchIn(recipe.title))
            assertTrue(recipe.ingredients.all { chinese.containsMatchIn(it.name) })
            assertTrue(recipe.steps.all { chinese.containsMatchIn(it.text) })
            assertNull(recipe.difficulty)
        }
        val miso = world.first { it.id == "world-miso-soup" }
        assertNull(miso.minutes)
        assertEquals("无", miso.servings)
        assertTrue(miso.ingredients.all { it.amount == "无" })
        assertEquals(listOf(miso), recipes.filteredBy(RecipeFilter(query = "MISO 日本")))
        assertNull(world.first { it.id == "world-fajitas" }.minutes)
    }

    @Test fun provenanceCoversEveryRecipeAndExistingFavoriteIdsSurvive() {
        val manifest = Json.parseToJsonElement(File(assets, "catalog_sources.json").readText()).jsonObject
        val rows = manifest.getValue("recipes").jsonArray.map { it.jsonObject }
        assertEquals(recipes.map { it.id }.toSet(), rows.map { it.getValue("recipeId").jsonPrimitive.content }.toSet())
        rows.forEach { row ->
            assertTrue(row.getValue("commit").jsonPrimitive.content.matches(Regex("[0-9a-f]{40}")))
            assertTrue(row.getValue("originalSha256").jsonPrimitive.content.matches(Regex("[0-9a-f]{64}")))
            assertEquals("Unlicense", row.getValue("license").jsonPrimitive.content)
        }
        val original =
            setOf(
                "braised-pork", "silky-eggs", "kung-pao", "steamed-fish", "three-treasures", "corn-soup",
                "mapo-tofu", "cauliflower", "greens", "cabbage", "shrimp", "tomato-tofu",
            )
        assertTrue(recipes.map { it.id }.containsAll(original))
    }
}
