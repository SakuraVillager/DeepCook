package com.deepcook.app

import com.deepcook.app.core.model.CoverOverrideFile
import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeStep
import com.deepcook.app.core.model.withCoverOverrides
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverOverrideTest {
    private fun dish(id: String, cover: String?) = Recipe(
        id = id,
        title = id,
        description = "",
        categories = listOf("家常菜"),
        servings = "2 人份",
        cover = cover,
        ingredients = listOf(Ingredient("i0", "材料", "1 个")),
        steps = listOf(RecipeStep("$id-s1", 1, "做", "做好。")),
    )

    @Test fun aSavedPhotoShadowsTheBundledPicture() {
        val recipes = listOf(dish("braised-pork", "photos/a.jpg"), dish("mapo-tofu", "photos/b.jpg"))
        val published = recipes.withCoverOverrides(mapOf("braised-pork" to "file:/data/covers/cover-1.jpg"))
        assertEquals("file:/data/covers/cover-1.jpg", published[0].cover)
        assertEquals("photos/b.jpg", published[1].cover)
    }

    @Test fun userRecipesKeepTheirOwnRecordedCover() {
        // A user recipe's cover lives in its own record; an override never shadows it.
        val published = listOf(dish("user-abc", "file:/data/covers/cover-2.jpg"))
            .withCoverOverrides(mapOf("user-abc" to "file:/data/covers/cover-3.jpg"))
        assertEquals("file:/data/covers/cover-2.jpg", published.single().cover)
    }

    @Test fun blankOrUnknownEntriesChangeNothing() {
        val published = listOf(dish("braised-pork", "photos/a.jpg"))
            .withCoverOverrides(mapOf("braised-pork" to "  ", "not-in-catalog" to "file:/x.jpg"))
        assertEquals("photos/a.jpg", published.single().cover)
    }

    @Test fun theOverrideFileRoundTripsThroughJson() {
        val json = Json { ignoreUnknownKeys = true }
        val file = CoverOverrideFile(entries = mapOf("braised-pork" to "file:/data/covers/cover-1.jpg"))
        assertEquals(file, json.decodeFromString<CoverOverrideFile>(json.encodeToString(file)))
    }
}
