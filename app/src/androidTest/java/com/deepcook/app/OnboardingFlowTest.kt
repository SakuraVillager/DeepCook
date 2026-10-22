package com.deepcook.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepcook.app.core.model.FoodSetup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val repository get() = (compose.activity.application as DeepCookApplication).container.userData

    @Before fun freshSetup() {
        runBlocking {
            repository.updateFoodSetup { FoodSetup() }
            repository.resetPreferences()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("你喜欢吃些什么？").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun next() = compose.onNodeWithTag("food-next").performClick()
    private fun choose(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    @Test fun skipAllIsOptionalAndDoesNotReturnAfterRecreation() {
        compose.onNodeWithText("跳过设置").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("精选菜谱").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("精选菜谱").assertIsDisplayed()
        assertTrue(runBlocking { repository.data.first().foodSetup.completed })
        assertNull(runBlocking { repository.data.first().foodSetup.profile.allergens })
    }

    @Test fun completeAllPagesRestoresDraftAndEditingCancelPreservesSavedAnswers() {
        choose("日韩料理")
        next()
        choose("花生")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("花生").assertIsSelected()
        compose.onAllNodesWithText("添加其他食材")[0].performScrollTo().performClick()
        compose.onNodeWithTag("custom-allergen").performTextInput("秋葵")
        compose.onNodeWithText("添加", substring = false).performClick()
        choose("香菜")
        next()
        choose("刚开始学")
        choose("30 min 内")
        choose("2 人")
        next()
        choose("快手解决一餐")
        next()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("合你口味").fetchSemanticsNodes().isNotEmpty() }
        val saved = runBlocking { repository.data.first().foodSetup.profile }
        assertEquals(setOf("花生", "秋葵"), saved.allergens)
        assertEquals(setOf("香菜"), saved.avoided)
        assertEquals(30, saved.minutes)
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithText("饮食偏好").performClick()
        compose.onNodeWithText("日韩料理").assertIsSelected()
        choose("中式家常")
        compose.onNodeWithText("取消").performClick()
        assertEquals(saved, runBlocking { repository.data.first().foodSetup.profile })
        compose.onNodeWithText("饮食偏好").performClick()
        choose("中式家常")
        repeat(3) { next() }
        next()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("饮食偏好").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("中式家常" in runBlocking { repository.data.first().foodSetup.profile.cuisines })
    }

    @Test fun explicitNoneIsExclusiveAndMidwaySkipKeepsEarlierAnswers() {
        choose("酸甜")
        choose("都喜欢")
        compose.onNodeWithText("酸甜").assertIsNotSelected()
        next()
        choose("没有已知项")
        choose("花生")
        compose.onNodeWithText("没有已知项").assertIsNotSelected()
        compose.onNodeWithText("跳过设置").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("合你口味").fetchSemanticsNodes().isNotEmpty() }
        val saved = runBlocking { repository.data.first().foodSetup.profile }
        assertEquals(setOf("都喜欢"), saved.tastes)
        assertEquals(setOf("花生"), saved.allergens)
    }
}
