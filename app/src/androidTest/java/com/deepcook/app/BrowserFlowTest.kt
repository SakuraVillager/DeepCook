package com.deepcook.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.data.AssetRecipeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun catalog(): List<Recipe> = runBlocking { AssetRecipeRepository(compose.activity).recipes() }

    @Before fun resetUserData() {
        val repo = (compose.activity.application as DeepCookApplication).container.userData
        runBlocking {
            val mine = (compose.activity.application as DeepCookApplication).container.userRecipes
            mine.recipes.first().forEach { mine.delete(it.id) }
            repo.clearFavorites()
            repo.clearRecent()
            repo.resetPreferences()
            repo.updateFoodSetup { com.deepcook.app.core.model.FoodSetup(completed = true) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("精选菜谱").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun searchByIngredientOpensRecipeAndReader() {
        val stepCount = catalog().first { it.id == "steamed-fish" }.steps.size
        compose.onNodeWithText("搜索菜名、食材").assertDoesNotExist()
        compose.onNodeWithText("从一道家常菜开始，好好吃饭。").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索菜谱").assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("鲈鱼")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("找到 1 道菜谱").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("清蒸鲈鱼").performClick()
        compose.onNodeWithTag("bottom-cooking-mode").performClick()
        compose.onNodeWithText("第 1 / $stepCount 步").assertIsDisplayed()
        compose.onNodeWithText("上一步").assertIsNotEnabled()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("第 2 / $stepCount 步").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("第 2 / $stepCount 步").assertIsDisplayed()
        compose.onNodeWithContentDescription("步骤目录").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("步骤 $stepCount"))
        compose.onNodeWithText("步骤 $stepCount").performClick()
        compose.onNodeWithText("返回菜谱").performClick()
        compose.onNodeWithText("步骤 $stepCount").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("找到 1 道菜谱").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("鲈鱼")
    }

    @Test fun favoritePersistsInCollectionAndKitchenHasNoFavoriteSummary() {
        val title = compose.onNodeWithText("红烧肉", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val favorite = compose.onAllNodesWithContentDescription("收藏菜谱")[0].getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(
            "Favorite action belongs in the cover's top-right corner",
            favorite.bottom <= title.top,
        )
        org.junit.Assert.assertTrue(favorite.left > title.left)
        compose.onAllNodesWithContentDescription("地域 ", substring = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("收藏菜谱")[0].performClick()
        compose.onNodeWithText("收藏", useUnmergedTree = true).performClick()
        compose.onNodeWithText("我的收藏 · 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("还没有收藏菜谱").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("还没有收藏菜谱").assertIsDisplayed()
        compose.onNodeWithText("撤销").performClick()
        compose.onNodeWithText("我的收藏 · 1").assertIsDisplayed()
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithText("我的厨房").assertIsDisplayed()
        compose.onNodeWithText("我的收藏", substring = false).assertDoesNotExist()
        compose.onNodeWithText("1 道想做的菜").assertDoesNotExist()
        compose.onNodeWithText("收藏", useUnmergedTree = true).performClick()
        compose.onNodeWithText("我的收藏 · 1").assertIsDisplayed()
    }

    @Test fun cancelFavoriteSnackbarDisappearsWithoutInteraction() {
        compose.onAllNodesWithContentDescription("收藏菜谱")[0].performClick()
        compose.onNodeWithText("收藏", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("已取消收藏").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("已取消收藏").assertIsDisplayed()
        compose.onNodeWithText("撤销").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitUntil(7_000) { compose.onAllNodesWithText("已取消收藏").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("撤销").assertDoesNotExist()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("还没有收藏菜谱").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("还没有收藏菜谱").assertIsDisplayed()
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

    @Test fun inlineSearchAlignsWithBackButtonAndKitchenHasNoStorageCard() {
        compose.onNodeWithContentDescription("搜索菜谱").performClick()
        val field = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
        val back = compose.onNodeWithContentDescription("返回").getUnclippedBoundsInRoot()
        val fieldHeight = field.bottom.value - field.top.value
        val backHeight = back.bottom.value - back.top.value
        org.junit.Assert.assertTrue(fieldHeight >= backHeight)
        org.junit.Assert.assertTrue(
            "Inline search should align with the 48dp back action; field=$fieldHeight, back=$backHeight",
            kotlin.math.abs(fieldHeight - 48f) <= 1f,
        )
        val fieldCenter = (field.top.value + field.bottom.value) / 2
        val backCenter = (back.top.value + back.bottom.value) / 2
        org.junit.Assert.assertEquals(backCenter, fieldCenter, 1f)
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithText("我的厨房").assertIsDisplayed()
        compose.onNodeWithText("保存在这台设备").assertDoesNotExist()
    }

    @Test fun emptyRecentFindRequestIsConsumedBeforeOpeningAnotherChild() {
        compose.onNodeWithText("红烧肉").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("查看全部"))
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

    @Test fun stepHeadingOpensCookingModeAndKeepsViewedStepOnReturn() {
        val stepCount = catalog().first { it.id == "braised-pork" }.steps.size
        compose.onNodeWithText("红烧肉").performClick()
        compose.onNodeWithText("步骤", substring = false).performClick()
        val tabs = compose.onNode(hasText("步骤", substring = false) and hasClickAction()).getUnclippedBoundsInRoot()
        val heading = compose.onNodeWithText("烹饪步骤 · 共 $stepCount 步").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("The pinned tabs must not cover the section heading", heading.top >= tabs.bottom)
        val entry = compose.onNodeWithTag("step-cooking-mode").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("Cooking mode belongs to the right of the step count", entry.left >= heading.right)
        compose.onAllNodesWithText("放大阅读", substring = true).assertCountEquals(0)
        compose.onNodeWithTag("step-cooking-mode").assertIsDisplayed().performClick()
        compose.onNodeWithText("第 1 / $stepCount 步").assertIsDisplayed()
        compose.onNodeWithText("上一步").assertIsNotEnabled()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("第 2 / $stepCount 步").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("步骤 2").assertIsDisplayed()
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
        compose.onNodeWithTag("bottom-cooking-mode").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("精选菜谱").assertIsDisplayed()
    }

    @Test fun fivePointDifficultyFilterUsesScoresAndCanBeCleared() {
        val recipes = catalog()
        compose.onNodeWithContentDescription("搜索菜谱").performClick()
        compose.onNodeWithText("难度", substring = false).performClick()
        compose.onNodeWithText("1/5", substring = false).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(
                "找到 ${recipes.count {
                    it.difficulty == 1
                }} 道菜谱",
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("1/5", substring = false).performClick()
        compose.onNodeWithText("3/5", substring = false).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(
                "找到 ${recipes.count {
                    it.difficulty == 3
                }} 道菜谱",
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("3/5", substring = false).performClick()
        compose.onNodeWithText("5/5", substring = false).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(
                "找到 ${recipes.count {
                    it.difficulty == 5
                }} 道菜谱",
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("清除筛选")[0].performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("找到 ${recipes.size} 道菜谱").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun recipeCategoriesCommitTogetherAndCancelPreservesDraft() {
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithText("我的菜谱", substring = false).performClick()
        compose.onNodeWithContentDescription("添加菜谱").performClick()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("分类", substring = false))
        compose.onNodeWithText("分类", substring = false).performClick()
        compose.onNodeWithText("确定").assertIsNotEnabled()
        compose.onNodeWithText("家常菜").performClick()
        compose.onNodeWithText("素食").performClick()
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("家常菜、素食").assertIsDisplayed()
        compose.onNodeWithText("分类", substring = false).performClick()
        compose.onNodeWithText("素食").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("家常菜、素食").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("分类", substring = false))
        compose.onNodeWithText("家常菜、素食").assertIsDisplayed()
    }

    @Test fun authoredRecipePrefillPreservesEditsThenSavesEditsAndDeletes() {
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithText("我的菜谱", substring = false).performClick()
        compose.onNodeWithContentDescription("添加菜谱").performClick()
        compose.onNode(hasSetTextAction() and hasText("菜名")).performTextInput("日式味噌汤")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasSetTextAction() and hasText("简介"))
        compose.onNode(hasSetTextAction() and hasText("简介")).performTextInput("这是我的手写做法")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("补全资料").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("已按「日式味噌汤」", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("日式味噌汤").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("日式味噌汤").assertIsDisplayed()
        compose.onNodeWithContentDescription("管理菜谱").performClick()
        compose.onNodeWithText("编辑菜谱").performClick()
        compose.onNode(hasSetTextAction() and hasText("菜名")).performTextReplacement("测试味噌汤")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasSetTextAction() and hasText("简介"))
        compose.onNode(hasSetTextAction() and hasText("简介")).assertTextContains("这是我的手写做法")
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("测试味噌汤").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("测试味噌汤").performClick()
        compose.onNodeWithText("我的菜谱 · 原始份量").assertIsDisplayed()
        compose.onNodeWithContentDescription("收藏菜谱").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("管理菜谱").performClick()
        compose.onNodeWithText("删除菜谱").performClick()
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("还没有自己的菜谱").fetchSemanticsNodes().isNotEmpty() }
        val container = (compose.activity.application as DeepCookApplication).container
        runBlocking {
            org.junit.Assert.assertTrue(container.userRecipes.recipes.first().isEmpty())
            org.junit.Assert.assertTrue(container.userData.data.first().favorites.isEmpty())
            org.junit.Assert.assertTrue(container.userData.data.first().recent.isEmpty())
        }
    }

    @Test fun internationalAliasOpensChineseRecipeWithMissingParameters() {
        compose.onNodeWithContentDescription("搜索菜谱").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("miso")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("日式味噌汤").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("难度无", substring = true).assertExists()
        compose.onNodeWithText("日式味噌汤").performClick()
        compose.onNodeWithText("Based Cooking · 中文译文 · 原始份量").assertIsDisplayed()
        compose.onNodeWithContentDescription("用时无", substring = true).assertExists()
        compose.onNodeWithTag("bottom-cooking-mode").performClick()
        compose.onNodeWithText("锅中加水至约半锅，煮沸。").assertIsDisplayed()
    }
}
