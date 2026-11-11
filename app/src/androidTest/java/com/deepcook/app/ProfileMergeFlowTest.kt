package com.deepcook.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepcook.app.core.model.FoodSetup
import com.deepcook.app.core.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileMergeFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val repository get() = (compose.activity.application as DeepCookApplication).container.userData

    @Before fun prepare() {
        (compose.activity.application as DeepCookApplication).container.signInForTest()
        runBlocking {
            repository.updateFoodSetup { FoodSetup(completed = true) }
            repository.updateProfile { UserProfile() }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("精选菜谱").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun profileNameAndSharedFoodPreferencesWorkWithoutRestoringRecipePreview() {
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithText("添加菜谱").assertDoesNotExist()
        compose.onNodeWithText("我的厨房").performClick()
        val name = compose.onNode(hasSetTextAction())
        name.performTextReplacement("小厨师")
        compose.waitUntil(5_000) { runBlocking { repository.data.first().profile.name == "小厨师" } }
        name.performTextClearance()
        compose.waitUntil(5_000) { runBlocking { repository.data.first().profile.displayName.isEmpty() } }
        name.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        name.performTextInput("小厨师")
        compose.onNodeWithText("饮食偏好").performScrollTo().performClick()
        compose.onNodeWithText("清淡").performScrollTo().performClick()
        repeat(4) { compose.onNodeWithTag("food-next").performClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("用户信息").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("清淡").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("小厨师").assertIsDisplayed()
        compose.onNodeWithText("我的菜谱", substring = false).performClick()
        compose.onNodeWithContentDescription("添加菜谱").assertIsDisplayed().performClick()
        compose.onNodeWithText("菜谱信息").assertIsDisplayed()
    }
}
