@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.deepcook.app

import androidx.datastore.core.DataStore
import androidx.lifecycle.SavedStateHandle
import com.deepcook.app.core.model.*
import com.deepcook.app.data.LocalUserDataRepository
import com.deepcook.app.data.UserDataSerializer
import com.deepcook.app.feature.onboarding.FoodProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class FoodProfileTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setMain() = Dispatchers.setMain(dispatcher)

    @After fun resetMain() = Dispatchers.resetMain()

    private class MemoryStore(initial: UserData = UserData()) : DataStore<UserData> {
        override val data = MutableStateFlow(initial)
        var fail = false
        override suspend fun updateData(transform: suspend (t: UserData) -> UserData): UserData {
            if (fail) throw IOException("disk full")
            return transform(data.value).also { data.value = it }
        }
    }

    private fun recipe(id: String, vararg names: String, minutes: Int? = null, featured: Boolean = true) = Recipe(
        id = id, title = id, description = "", region = "中式", categories = listOf("家常菜"),
        servings = "2 人", minutes = minutes, featured = featured,
        ingredients = names.mapIndexed { index, name -> Ingredient("i$index", name, "适量") },
        steps = listOf(RecipeStep("s1", 1, "处理", "处理食材")),
    )

    @Test fun freshInstallStartsSetupButLegacyDataMigratesWithoutLosingFavorites() = runTest {
        assertFalse(UserDataSerializer.defaultValue.foodSetup.completed)
        val old = """{"favorites":[{"recipeId":"kept","timestamp":12}],"recent":[],"preferences":{}}"""
        val migrated = UserDataSerializer.readFrom(ByteArrayInputStream(old.toByteArray()))
        assertTrue(migrated.foodSetup.completed)
        assertEquals("kept", migrated.favorites.single().recipeId)
    }

    @Test fun serializationDistinguishesUnansweredFromExplicitNoAllergiesAndKeepsCustomFoods() = runTest {
        for (allergens in listOf(null, emptySet(), setOf("花生", "秋葵"))) {
            val original = UserData(foodSetup = FoodSetup(profile = FoodProfile(allergens = allergens)))
            val output = ByteArrayOutputStream()
            UserDataSerializer.writeTo(original, output)
            assertEquals(original, UserDataSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
        }
    }

    @Test fun teammateProfileTagsMigrateToSharedFoodPreferencesWithoutInventingAllergies() = runTest {
        val old = """{"profile":{"displayName":"小厨师","likes":["甜","鲜"],"avoids":["海鲜","香菜"]}}"""
        val migrated = UserDataSerializer.readFrom(ByteArrayInputStream(old.toByteArray()))
        assertEquals("小厨师", migrated.profile.name)
        assertEquals(setOf("甜", "鲜"), migrated.foodSetup.profile.tastes)
        assertEquals(setOf("海鲜", "香菜"), migrated.foodSetup.profile.avoided)
        assertNull(migrated.foodSetup.profile.allergens)
        assertTrue(migrated.foodSetup.completed)
        assertFalse(recipe("seafood", "虾仁").matchesFoodExclusions(migrated.foodSetup.profile))
    }

    @Test fun rapidDraftChangesThenSkipKeepAllAnswersAndClearDraft() = runTest {
        val store = MemoryStore()
        val vm = FoodProfileViewModel(LocalUserDataRepository(store), SavedStateHandle(), false)
        advanceUntilIdle()
        vm.change { it.copy(cuisines = setOf("日韩料理")) }
        vm.page(1)
        vm.change { it.copy(allergens = setOf("花生"), avoided = setOf("香菜")) }
        vm.finish()
        advanceUntilIdle()
        assertTrue(store.data.value.foodSetup.completed)
        assertNull(store.data.value.foodSetup.draft)
        assertEquals(setOf("花生"), store.data.value.foodSetup.profile.allergens)
        assertEquals(setOf("日韩料理"), store.data.value.foodSetup.profile.cuisines)
        assertTrue(vm.state.value.done)
    }

    @Test fun newInstanceRestoresPersistedDraftAndPage() = runTest {
        val store = MemoryStore()
        val repo = LocalUserDataRepository(store)
        val first = FoodProfileViewModel(repo, SavedStateHandle(), false)
        advanceUntilIdle()
        first.change { it.copy(tastes = setOf("酸甜")) }
        first.page(2)
        advanceUntilIdle()
        val recreated = FoodProfileViewModel(repo, SavedStateHandle(), false)
        advanceUntilIdle()
        assertEquals(2, recreated.state.value.page)
        assertEquals(setOf("酸甜"), recreated.state.value.profile.tastes)
        assertFalse(store.data.value.foodSetup.completed)
    }

    @Test fun editDoesNotCommitUntilSaveAndAppearanceResetPreservesFoodProfile() = runTest {
        val original = FoodProfile(avoided = setOf("香菜"))
        val store = MemoryStore(UserData(foodSetup = FoodSetup(profile = original, completed = true)))
        val repo = LocalUserDataRepository(store)
        val vm = FoodProfileViewModel(repo, SavedStateHandle(), true)
        advanceUntilIdle()
        vm.change { it.copy(avoided = setOf("茄子")) }
        advanceUntilIdle()
        assertEquals(original, store.data.value.foodSetup.profile)
        vm.finish()
        advanceUntilIdle()
        repo.resetPreferences()
        assertEquals(setOf("茄子"), store.data.value.foodSetup.profile.avoided)
    }

    @Test fun failedFinishKeepsChoicesAndCanRetryWithoutFalseCompletion() = runTest {
        val store = MemoryStore()
        val vm = FoodProfileViewModel(LocalUserDataRepository(store), SavedStateHandle(), false)
        advanceUntilIdle()
        vm.change { it.copy(diet = "纯素") }
        advanceUntilIdle()
        store.fail = true
        vm.finish()
        advanceUntilIdle()
        assertFalse(vm.state.value.done)
        assertFalse(store.data.value.foodSetup.completed)
        assertEquals("纯素", vm.state.value.profile.diet)
        assertNotNull(vm.state.value.error)
        store.fail = false
        vm.finish()
        advanceUntilIdle()
        assertTrue(vm.state.value.done)
    }

    @Test fun exclusionsCoverIngredientAliasesAndRejectMissingIngredientData() {
        val profile = FoodProfile(allergens = setOf("大豆"), avoided = setOf("猪肉"))
        assertFalse(recipe("soy", "生抽").matchesFoodExclusions(profile))
        assertFalse(recipe("pork", "五花肉").matchesFoodExclusions(profile))
        assertFalse(recipe("unknown", "无").matchesFoodExclusions(profile))
        assertFalse(recipe("empty").matchesFoodExclusions(profile))
        assertTrue(recipe("tomato", "西红柿").matchesFoodExclusions(profile))
        assertFalse(recipe("chili", "小米椒").matchesFoodExclusions(FoodProfile(spice = "不吃辣")))
    }

    @Test fun vegetarianAndVeganRespectTheirDifferentChoices() {
        val egg = recipe("egg", "鸡蛋", "牛奶")
        assertTrue(egg.matchesFoodExclusions(FoodProfile(diet = "蛋奶素")))
        assertFalse(egg.matchesFoodExclusions(FoodProfile(diet = "纯素")))
        assertFalse(recipe("fish", "蚝油").matchesFoodExclusions(FoodProfile(diet = "蛋奶素")))
    }

    @Test fun recommendationAppliesExclusionsThenRanksKnownTimeAndKeepsCatalogUntouched() {
        val recipes =
            listOf(
                recipe("slow", "西红柿", minutes = 60),
                recipe("fast", "青菜", minutes = 15),
                recipe("excluded", "花生", minutes = 5),
            )
        val profile = FoodProfile(minutes = 30, allergens = setOf("花生"))
        assertEquals(listOf("fast", "slow"), recipes.forFoodProfile(profile).map { it.id })
        assertEquals(3, recipes.size)
        assertEquals(recipes, recipes.forFoodProfile(FoodProfile()))
    }
}
