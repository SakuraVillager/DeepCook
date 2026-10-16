package com.deepcook.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetUserData() {
        val repo = (compose.activity.application as DeepCookApplication).container.userData
        runBlocking {
            repo.clearFavorites()
            repo.clearRecent()
            repo.resetPreferences()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("精选菜谱").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun searchByIngredientOpensRecipeAndReader() {
        compose.onNodeWithText("搜索菜名、食材").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索菜谱").assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("鲈鱼")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("找到 1 道菜谱").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("清蒸鲈鱼").performClick()
        compose.onNodeWithText("分步阅读").performClick()
        compose.onNodeWithText("第 1 / 4 步").assertIsDisplayed()
        compose.onNodeWithText("上一步").assertIsNotEnabled()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("第 2 / 4 步").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("第 2 / 4 步").assertIsDisplayed()
        compose.onNodeWithContentDescription("步骤目录").performClick()
        compose.onNodeWithText("淋汁上桌").performClick()
        compose.onNodeWithText("返回菜谱").performClick()
        compose.onNodeWithText("放大阅读 · 第 4 步").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("找到 1 道菜谱").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("鲈鱼")
    }

    @Test fun favoriteAppearsInCollectionAndKitchenAndCanBeUndone() {
        compose.onAllNodesWithContentDescription("收藏菜谱")[0].performClick()
        compose.onNodeWithText("收藏", useUnmergedTree = true).performClick()
        compose.onNodeWithText("我的收藏 · 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithText("还没有收藏菜谱").assertIsDisplayed()
        compose.onNodeWithText("撤销").performClick()
        compose.onNodeWithText("我的收藏 · 1").assertIsDisplayed()
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithText("1 道想做的菜").assertIsDisplayed()
    }

    @Test fun appearanceAndReadingSettingsPersistAcrossRecreation() {
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithText("深色").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("深色").assertIsSelected()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("阅读").performClick()
        compose.onNodeWithText("正文字号").performClick()
        compose.onNodeWithText("特大").performClick()
        compose.onNodeWithText("特大").assertIsDisplayed()
        compose.onNodeWithText("显示步骤图片").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("显示步骤图片") and isToggleable() and isOff())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("显示步骤图片") and isToggleable()).assertIsOff()
    }

    @Test fun tabsRestoreSearchAndChildPagesReturnToTheirOriginalTab() {
        compose.onNodeWithContentDescription("搜索菜谱").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("鲈鱼")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("找到 1 道菜谱").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithText("我的厨房").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("阅读").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("使用偏好").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("我的厨房").assertIsDisplayed()
        compose.onNodeWithText("找菜", useUnmergedTree = true).performClick()
        compose.onNodeWithText("找到 1 道菜谱").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("鲈鱼")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("搜索菜谱").assertIsDisplayed()
    }

    @Test fun siblingTabsFadeWithoutTranslationAndKeepNavigationInPlace() {
        val tab = compose.onNode(hasText("我的") and hasClickAction())
        val navigationBefore = tab.getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        try {
            tab.performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(100)
            val during = compose.onNodeWithText("我的厨房").getUnclippedBoundsInRoot()
            val navigationDuring = tab.getUnclippedBoundsInRoot()
            compose.mainClock.advanceTimeBy(250)
            val after = compose.onNodeWithText("我的厨房").getUnclippedBoundsInRoot()
            org.junit.Assert.assertEquals(after.left.value, during.left.value, .5f)
            org.junit.Assert.assertEquals(navigationBefore, navigationDuring)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun hierarchicalPagesSlideForwardAndReverseOnBack() {
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("设置").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("设置").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(120)
            val entering = compose.onNodeWithText("使用偏好").getUnclippedBoundsInRoot()
            compose.mainClock.advanceTimeBy(350)
            val settled = compose.onNodeWithText("使用偏好").getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue("Child page must enter from the right", entering.left > settled.left)

            compose.onNodeWithText("外观").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(350)
            compose.onNodeWithText("明暗模式").assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(120)
            val returning = compose.onNodeWithText("使用偏好").getUnclippedBoundsInRoot()
            compose.mainClock.advanceTimeBy(350)
            val returned = compose.onNodeWithText("使用偏好").getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue("Back must reverse the shared axis", returning.left < returned.left)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun compactSearchAlignsWithBackButtonAndKitchenHasNoStorageCard() {
        compose.onNodeWithContentDescription("搜索菜谱").performClick()
        val field = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
        val back = compose.onNodeWithContentDescription("返回").getUnclippedBoundsInRoot()
        val fieldHeight = field.bottom.value - field.top.value
        val backHeight = back.bottom.value - back.top.value
        org.junit.Assert.assertTrue(fieldHeight >= backHeight)
        org.junit.Assert.assertTrue(
            "Compact search should be 52dp high; field=$fieldHeight, back glyph container=$backHeight",
            kotlin.math.abs(fieldHeight - 52f) <= 1f,
        )
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithText("我的厨房").assertIsDisplayed()
        compose.onNodeWithText("保存在这台设备").assertDoesNotExist()
    }

    @Test fun emptyRecentFindRequestIsConsumedBeforeOpeningAnotherChild() {
        compose.onNodeWithText("红烧肉").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithText("查看全部").performClick()
        compose.onNodeWithContentDescription("移除浏览记录").performClick()
        compose.onNodeWithText("去找菜").performClick()
        // The outer pop restores Home, then its saved command changes tabs.
        // Wait for both independently scheduled transitions to become visible.
        try {
            compose.waitUntil(5_000) {
                runCatching { compose.onNodeWithContentDescription("搜索菜谱").assertIsDisplayed() }.isSuccess
            }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError(compose.onRoot().printToString(), failure)
        }
        compose.onNodeWithContentDescription("搜索菜谱").assertIsDisplayed()
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("我的厨房").assertIsDisplayed()
    }

    @Test fun stepCardsOpenTheSelectedStepAndKeepItsPlaceOnReturn() {
        compose.onNodeWithText("红烧肉").performClick()
        compose.onNodeWithText("步骤", substring = false).performClick()
        val tabs = compose.onNode(hasText("步骤", substring = false) and hasClickAction()).getUnclippedBoundsInRoot()
        val heading = compose.onNodeWithText("烹饪步骤 · 共 5 步").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("The pinned tabs must not cover the section heading", heading.top >= tabs.bottom)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("放大阅读 · 第 2 步"))
        compose.onNodeWithText("放大阅读 · 第 2 步").performClick()
        compose.onNodeWithText("第 2 / 5 步").assertIsDisplayed()
        compose.onNodeWithText("煸出油脂").assertIsDisplayed()
        compose.onNodeWithText("上一步").performClick()
        compose.onNodeWithText("第 1 / 5 步").assertIsDisplayed()
        compose.onNodeWithText("上一步").assertIsNotEnabled()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("放大阅读 · 第 1 步").assertIsDisplayed()
        compose.onNodeWithText("来源", substring = false).performClick()
        compose.onNodeWithText("内容与来源").assertIsDisplayed()
    }

    @Test fun repeatedRecipeTapDoesNotCreateDuplicateDetailPages() {
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("红烧肉").performTouchInput { doubleClick() }
            compose.mainClock.advanceTimeBy(500)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithText("分步阅读").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("精选菜谱").assertIsDisplayed()
    }

    @Test fun fivePointDifficultyFilterUsesScoresAndCanBeCleared() {
        compose.onNodeWithContentDescription("搜索菜谱").performClick()
        compose.onNodeWithText("难度", substring = false).performClick()
        compose.onNodeWithText("1/5", substring = false).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("找到 4 道菜谱").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1/5", substring = false).performClick()
        compose.onNodeWithText("3/5", substring = false).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("找到 7 道菜谱").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("3/5", substring = false).performClick()
        compose.onNodeWithText("5/5", substring = false).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("找到 0 道菜谱").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("清除筛选")[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("找到 12 道菜谱").fetchSemanticsNodes().isNotEmpty() }
    }
}
