package com.deepcook.app

import com.deepcook.app.core.model.AiOverlay
import com.deepcook.app.core.model.DishNote
import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeStep
import com.deepcook.app.core.model.UserData
import com.deepcook.app.core.model.UserPreferences
import com.deepcook.app.core.model.UserProfile
import com.deepcook.app.data.AiOverlayStore
import com.deepcook.app.data.UserDataRepository
import com.deepcook.app.data.ai.AiEvent
import com.deepcook.app.data.ai.AiGateway
import com.deepcook.app.data.ai.AiMessage
import com.deepcook.app.feature.recipe.DishAiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DishAiViewModelTest {
    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun dish(servings: String = "4 人份") = Recipe(
        id = "test-dish",
        region = "中式",
        title = "红烧肉",
        description = "",
        categories = listOf("家常菜"),
        minutes = 60,
        difficulty = 3,
        servings = servings,
        ingredients = listOf(Ingredient("i0", "五花肉", "500 克"), Ingredient("i1", "蚝油", "1 汤匙")),
        steps = listOf(RecipeStep("s1", 1, "焯水", "冷水下锅。"), RecipeStep("s2", 2, "收汁", "大火收汁。")),
        notes = listOf("糖色别炒过头"),
    )

    private fun viewModel(
        gateway: AiGateway,
        recipe: Recipe = dish(),
        overlays: AiOverlayStore = FakeOverlays(),
    ) = DishAiViewModel(recipe, gateway, FakeUserData(), overlays)

    @Test fun aKnownSwapIsAnsweredWithoutTheModel() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeGateway(listOf(AiEvent.Delta("不应该走到这里")))
        val vm = viewModel(gateway)
        vm.askSubstitute(Ingredient("i1", "蚝油", "1 汤匙"))
        testScheduler.advanceUntilIdle()
        val answer = vm.state.value.answer!!
        assertEquals(DishAiViewModel.Source.LOCAL, answer.source)
        assertTrue(answer.text.isNotEmpty())
        assertEquals(0, gateway.calls)
    }

    @Test fun anOfflineFailureFallsBackToTheRecipeText() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(FakeGateway(listOf(AiEvent.Offline)))
        vm.askDiagnosis("我做的为什么发苦")
        testScheduler.advanceUntilIdle()
        val answer = vm.state.value.answer!!
        assertEquals(DishAiViewModel.Source.FALLBACK, answer.source)
        assertTrue(answer.text.contains("先按菜谱"))
        assertTrue(answer.text.contains("糖色别炒过头"))
        assertTrue(answer.hint!!.contains("没网"))
        assertFalse(answer.thinking)
    }

    @Test fun theDeadlineShowsTheRecipeWhileTheModelIsStillQueued() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(SilentGateway())
        vm.askDiagnosis("我做的为什么发苦")
        testScheduler.advanceTimeBy(2_600)
        testScheduler.runCurrent()
        val midway = vm.state.value.answer!!
        assertEquals(DishAiViewModel.Source.FALLBACK, midway.source)
        assertTrue(midway.thinking)
        assertTrue(midway.hint!!.contains("排队"))
        // The request is still running; leaving the page must cancel it.
        vm.dismiss()
        assertNull(vm.state.value.answer)
    }

    @Test fun aStreamedAnswerIsCutToTwoSentences() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeGateway(
            listOf(
                AiEvent.Delta("先焯水再炒。"),
                AiEvent.Delta("糖色别炒糊。第三句多余。"),
                AiEvent.Done,
            ),
        )
        val vm = viewModel(gateway)
        vm.askDiagnosis("我做的为什么发苦")
        testScheduler.advanceUntilIdle()
        val answer = vm.state.value.answer!!
        assertEquals(DishAiViewModel.Source.AI, answer.source)
        assertEquals("先焯水再炒。糖色别炒糊。", answer.text)
        assertNull(answer.hint)
        assertEquals(1, gateway.calls)
    }

    @Test fun choosingTwoServingsHalvesThePrintedAmounts() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(FakeGateway())
        val ingredient = Ingredient("i0", "五花肉", "500 克")
        assertEquals("500 克", vm.amountOf(ingredient))
        vm.selectServings(2)
        assertEquals("250 克", vm.amountOf(ingredient))
        vm.selectServings(null)
        assertEquals("500 克", vm.amountOf(ingredient))
    }

    @Test fun aCachedServingCountUnlocksScalingForAnUnlabelledDish() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(
            FakeGateway(),
            recipe = dish(servings = "无"),
            overlays = FakeOverlays(AiOverlay(recipeId = "test-dish", servings = "2 人份")),
        )
        testScheduler.advanceUntilIdle()
        assertEquals(2.0, vm.state.value.servingsBase!!, 0.001)
        vm.selectServings(4)
        assertEquals("1000 克", vm.amountOf(Ingredient("i0", "五花肉", "500 克")))
    }

    @Test fun theFallbackQuotesThePrintedAmountNotTheScaledOne() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(FakeGateway(listOf(AiEvent.Offline)))
        vm.selectServings(2)
        // 牛腩 is not in the local swap table, so this one goes to the model and then falls back.
        vm.askSubstitute(Ingredient("i9", "牛腩", "500 克"))
        testScheduler.advanceUntilIdle()
        val answer = vm.state.value.answer!!
        assertEquals(DishAiViewModel.Source.FALLBACK, answer.source)
        assertTrue(answer.text.contains("500 克"))
        assertFalse(answer.text.contains("250 克"))
    }

    private class FakeGateway(private val events: List<AiEvent> = emptyList()) : AiGateway {
        var calls = 0
            private set

        override fun stream(messages: List<AiMessage>, model: String?): Flow<AiEvent> {
            calls++
            return flow { events.forEach { emit(it) } }
        }

        override suspend fun complete(messages: List<AiMessage>): String? = null
    }

    private class SilentGateway : AiGateway {
        override fun stream(messages: List<AiMessage>, model: String?): Flow<AiEvent> =
            flow { awaitCancellation() }

        override suspend fun complete(messages: List<AiMessage>): String? = null
    }

    private class FakeUserData(initial: UserData = UserData()) : UserDataRepository {
        override val data: Flow<UserData> = MutableStateFlow(initial)
        override suspend fun setFavorite(id: String, favorite: Boolean, timestamp: Long) = Unit
        override suspend fun recordVisit(id: String) = Unit
        override suspend fun removeVisit(id: String) = Unit
        override suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) = Unit
        override suspend fun updateProfile(transform: (UserProfile) -> UserProfile) = Unit
        override suspend fun addDishNote(note: DishNote) = Unit
        override suspend fun updateDishNote(id: String, text: String, now: Long) = Unit
        override suspend fun removeDishNote(id: String) = Unit
        override suspend fun clearRecent() = Unit
        override suspend fun clearFavorites() = Unit
        override suspend fun resetPreferences() = Unit
        override suspend fun forget(id: String) = Unit
    }

    private class FakeOverlays(private val overlay: AiOverlay? = null) : AiOverlayStore {
        override val entries: Flow<Map<String, AiOverlay>> = MutableStateFlow(emptyMap())
        override suspend fun get(recipeId: String): AiOverlay? = overlay?.takeIf { it.recipeId == recipeId }
        override suspend fun put(overlay: AiOverlay) = Unit
        override suspend fun clear() = Unit
    }
}
