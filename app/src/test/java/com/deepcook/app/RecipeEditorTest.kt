@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.deepcook.app

import androidx.lifecycle.SavedStateHandle
import com.deepcook.app.core.model.*
import com.deepcook.app.data.*
import com.deepcook.app.data.lookup.*
import com.deepcook.app.feature.authoring.RecipeEditorViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RecipeEditorTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setMain() = Dispatchers.setMain(dispatcher)

    @After fun resetMain() = Dispatchers.resetMain()

    private val original = RecipeDraft(
        title = "我的煎蛋",
        categories = listOf("早餐"),
        ingredients = listOf(DraftIngredient("鸡蛋", "2 个")),
        steps = listOf(DraftStep("煎蛋", "煎至熟透")),
        cover = "old-cover",
    ).toRecipe("user-original")

    private class Store(initial: List<Recipe>) : UserRecipeStore {
        override val recipes = MutableStateFlow(initial)
        var failing = false
        override suspend fun save(recipe: Recipe) {
            if (failing) throw IOException("disk full")
            recipes.value = listOf(recipe)
        }
        override suspend fun delete(id: String) {
            recipes.value = recipes.value.filterNot { it.id == id }
        }
    }
    private class Covers : RecipeCovers {
        val deleted = mutableListOf<String?>()
        override suspend fun import(uri: String) = "new-cover"
        override suspend fun delete(cover: String?) {
            deleted += cover
        }
    }
    private val missing = object : RecipeLookup {
        override suspend fun lookup(name: String) = LookupOutcome.NotFound
    }

    @Test fun cancellingCoverReplacementKeepsCommittedPhoto() = runTest {
        val covers = Covers()
        val store = Store(listOf(original))
        val editor = RecipeEditorViewModel(missing, covers, store, original.id, original, SavedStateHandle())
        editor.importCover("picked-photo")
        advanceUntilIdle()
        assertTrue(covers.deleted.isEmpty())
        assertEquals("old-cover", store.recipes.value.single().cover)
        editor.discard()
        advanceUntilIdle()
        assertEquals(listOf("new-cover"), covers.deleted)
        assertEquals("old-cover", store.recipes.value.single().cover)
    }

    @Test fun failedSaveRetainsDraftAndOriginalCoverAndCanRetry() = runTest {
        val covers = Covers()
        val store = Store(listOf(original)).apply { failing = true }
        val editor = RecipeEditorViewModel(missing, covers, store, original.id, original, SavedStateHandle())
        editor.importCover("picked-photo")
        advanceUntilIdle()
        editor.save()
        advanceUntilIdle()
        assertFalse(editor.state.value.saving)
        assertTrue(editor.state.value.errors.isNotEmpty())
        assertEquals("new-cover", editor.state.value.draft.cover)
        assertEquals(original, store.recipes.value.single())
        assertTrue(covers.deleted.isEmpty())
        store.failing = false
        editor.save()
        advanceUntilIdle()
        assertEquals("new-cover", store.recipes.value.single().cover)
        assertEquals(listOf("old-cover"), covers.deleted)
        assertFalse(editor.state.value.dirty)
    }

    @Test fun firstPrefillAlsoPreservesHandwrittenFields() {
        val current = RecipeDraft(
            title = "我的做法",
            servings = "3 人份",
            description = "手写简介",
            ingredients = listOf(DraftIngredient("手写食材", "")),
        )
        val filled = RecipeDraft.from(original).copy(servings = "1 人份", minutes = 10)
        val merged = RecipeEditorViewModel.fillBlanks(current, filled)
        assertEquals(current.title, merged.title)
        assertEquals(current.servings, merged.servings)
        assertEquals(current.description, merged.description)
        assertEquals(current.ingredients, merged.ingredients)
        assertEquals(10, merged.minutes)
        assertEquals(filled.steps, merged.steps)
    }

    @Test fun changingNameWhileLookupRunsRejectsStaleResponse() = runTest {
        val waiting = CompletableDeferred<LookupOutcome>()
        val lookup = object : RecipeLookup {
            override suspend fun lookup(name: String) = waiting.await()
        }
        val editor = RecipeEditorViewModel(lookup, Covers(), Store(emptyList()), "user-new", null, SavedStateHandle())
        editor.edit { it.copy(title = "煎蛋") }
        editor.prefill()
        runCurrent()
        editor.edit { it.copy(title = "红烧肉") }
        waiting.complete(LookupOutcome.Filled(RecipeDraft.from(original), "煎蛋", "", "test"))
        advanceUntilIdle()
        assertEquals("红烧肉", editor.state.value.draft.title)
        assertFalse(editor.state.value.looking)
        assertEquals(listOf(DraftStep()), editor.state.value.draft.steps)
    }

    @Test fun savedStateRestoresUnsavedDraftAndStagedCover() = runTest {
        val saved = SavedStateHandle()
        val store = Store(listOf(original))
        val covers = Covers()
        val editor = RecipeEditorViewModel(missing, covers, store, original.id, original, saved)
        editor.edit { it.copy(title = "修改尚未保存", minutes = 42) }
        editor.importCover("picked")
        advanceUntilIdle()
        val restoredState = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val restored = RecipeEditorViewModel(missing, covers, store, original.id, original, restoredState)
        assertEquals(editor.state.value.draft, restored.state.value.draft)
        assertTrue(restored.state.value.dirty)
        restored.discard()
        advanceUntilIdle()
        assertEquals(listOf("new-cover"), covers.deleted)
        assertEquals(original, store.recipes.value.single())
    }

    @Test fun editingPreservesStepIdsAndAvoidsCollisionsAfterRowRemoval() {
        val two = original.copy(
            steps =
            original.steps + original.steps.single().copy(id = "user-original-s2", order = 2),
        )
        val draft = RecipeDraft.from(two)
        val edited = draft.copy(steps = listOf(draft.steps[1], DraftStep("新步骤", "继续处理"))).toRecipe(original.id)
        assertEquals("user-original-s2", edited.steps.first().id)
        assertEquals(2, edited.steps.map { it.id }.distinct().size)
        assertEquals(original.version + 1, edited.version)
    }

    @Test fun unknownFieldsStayUnknownAndTitleAloneIsNotAStep() {
        val draft = RecipeDraft.from(original).copy(servings = "", minutes = null, difficulty = null)
        val recipe = draft.toRecipe(original.id)
        assertEquals("无", recipe.servings)
        assertEquals("无", recipe.region)
        assertNull(recipe.minutes)
        assertTrue(draft.copy(steps = listOf(DraftStep("只有标题", ""))).problems().any { it.contains("正文") })
    }
}
