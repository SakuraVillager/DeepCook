package com.deepcook.app

import androidx.datastore.core.CorruptionException
import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeStep
import com.deepcook.app.data.UserRecipeBook
import com.deepcook.app.data.UserRecipeBookSerializer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class UserRecipeStoreTest {
    private fun recipe(id: String, title: String = "我的菜") = Recipe(
        id = id,
        version = 3,
        title = title,
        description = "自己写的",
        categories = listOf("家常菜"),
        servings = "1 人份",
        ingredients = listOf(Ingredient("i0", "鸡蛋", "1 个")),
        steps = listOf(RecipeStep("$id-s1", 1, "打蛋", "打散")),
    )

    @Test fun savingReplacesTheSameIdAndAppendsNewOnesInOrder() {
        val book = UserRecipeBook()
            .withRecipe(recipe("user-a", "A"))
            .withRecipe(recipe("user-b", "B"))
        assertEquals(listOf("user-a", "user-b"), book.recipes.map { it.id })

        val edited = book.withRecipe(recipe("user-a", "A2"))
        assertEquals(listOf("user-a", "user-b"), edited.recipes.map { it.id })
        assertEquals("A2", edited.recipes.first().title)
    }

    @Test fun deletingLeavesTheRestAlone() {
        val book = UserRecipeBook()
            .withRecipe(recipe("user-a"))
            .withRecipe(recipe("user-b"))
            .without("user-a")
        assertEquals(listOf("user-b"), book.recipes.map { it.id })
        assertEquals(book, book.without("missing"))
    }

    @Test fun serializerKeepsEveryFieldOfTheUserRecipes() = runBlocking {
        val original = UserRecipeBook(listOf(recipe("user-a"), recipe("user-b", "B")))
        val output = ByteArrayOutputStream()
        UserRecipeBookSerializer.writeTo(original, output)
        val restored = UserRecipeBookSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(original, restored)
        assertEquals("user-b", restored.recipes[1].id)
        assertEquals(listOf("i0"), restored.recipes[0].ingredients.map { it.id })
        assertEquals(listOf("user-a-s1"), restored.recipes[0].steps.map { it.id })
    }

    @Test fun anUnreadableFileIsReportedInsteadOfBeingReplaced() = runBlocking {
        try {
            UserRecipeBookSerializer.readFrom(ByteArrayInputStream("not json".toByteArray()))
            fail("Corrupt data must be reported")
        } catch (_: CorruptionException) {
            // Expected: the file is kept for inspection.
        }
    }

    @Test fun contentThatFailsValidationIsAlsoReported() = runBlocking {
        // A region outside the whitelist throws from Recipe.init; it must not crash the app.
        val broken = """{"recipes":[{"id":"user-x","title":"t","description":"d","region":"火星",
            |"categories":["家常菜"],"servings":"1 人份",
            |"ingredients":[{"id":"i0","name":"蛋","amount":"1 个"}],
            |"steps":[{"id":"user-x-s1","order":1,"title":"s","text":"x"}]}]}
        """.trimMargin()
        try {
            UserRecipeBookSerializer.readFrom(ByteArrayInputStream(broken.toByteArray()))
            fail("Invalid content must be reported")
        } catch (_: CorruptionException) {
            // Expected.
        }
    }
}
