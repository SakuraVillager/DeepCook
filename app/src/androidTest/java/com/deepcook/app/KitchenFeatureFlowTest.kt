package com.deepcook.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KitchenFeatureFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as DeepCookApplication).container

    @Before fun prepare() {
        grantTimerPermissions(compose.activity)
        runBlocking {
            container.timers.restore()
            container.timers.timers.value.forEach { container.timers.remove(it.id) }
            container.userData.resetPreferences()
            container.userData.updateFoodSetup { com.deepcook.app.core.model.FoodSetup(completed = true) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                container.kitchenDatabase.writableDatabase.execSQL("DELETE FROM ratings WHERE user_key='local'")
            }
            container.kitchen.observe("braised-pork").first().posts.filterNot {
                it.sample
            }.forEach { container.kitchen.deletePost(it.id) }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("红烧肉").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun nutritionRatingAndLocalCommentWorkFromRecipeDetails() {
        compose.onNodeWithText("红烧肉").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("评价一下"))
        compose.onNodeWithText("评价一下").performClick()
        compose.onNodeWithText("保存评分").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("我的评分 8/10").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("我的评分 8/10").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("每份营养"))
        compose.onNodeWithText("每份营养").assertIsDisplayed()
        compose.onNodeWithText("查看全部", substring = false).performClick()
        compose.onNodeWithText("每份营养成分").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("来源", substring = false).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("add-comment"))
        compose.onNodeWithTag("add-comment").performClick()
        compose.onNodeWithTag("community-text").performTextInput("这是一条本机评论")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("community-text").assertTextContains("这是一条本机评论")
        compose.onNodeWithText("发布").performClick()
        compose.waitUntil(5_000) {
            runBlocking { container.kitchen.observe("braised-pork").first().posts.any { it.text == "这是一条本机评论" } }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("community-text").fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("add-submission"))
        compose.onNodeWithTag("add-submission").performClick()
        compose.onNodeWithTag("community-text").performTextInput("这是我的下厨作业")
        compose.onNodeWithText("发布").performClick()
        compose.waitUntil(5_000) {
            runBlocking {
                container.kitchen.observe("braised-pork").first().posts.any {
                    it.submission &&
                        it.text == "这是我的下厨作业"
                }
            }
        }
    }

    @Test fun ratingNutritionAndPinnedTabsKeepTheirLayout() {
        compose.onNodeWithText("红烧肉").performClick()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(1)
        val score = compose.onNodeWithTag("rating-average").getUnclippedBoundsInRoot()
        val count = compose.onNodeWithTag("rating-count").getUnclippedBoundsInRoot()
        val great = compose.onNodeWithText("好极了").getUnclippedBoundsInRoot()
        val good = compose.onNodeWithText("挺好").getUnclippedBoundsInRoot()
        val average = compose.onNodeWithText("一般").getUnclippedBoundsInRoot()
        assertEquals(great.top.value, score.top.value, 1f)
        assertEquals(good.bottom.value, score.bottom.value, 1f)
        assertEquals(average.top.value, count.top.value, 1f)
        compose.onNodeWithText("满分", substring = true).assertDoesNotExist()
        compose.onNodeWithText("收藏数、评分和互动含本地示例数据", substring = true).assertDoesNotExist()
        val nutrition = compose.onNodeWithText("每份营养").getUnclippedBoundsInRoot()
        val header = compose.onNodeWithTag("detail-section-header").getUnclippedBoundsInRoot()
        assertTrue(nutrition.top >= count.bottom)
        val columns = (0..3).map { compose.onNodeWithTag("nutrition-column-$it").getUnclippedBoundsInRoot() }
        columns.forEach {
            assertEquals((columns.first().right - columns.first().left).value, (it.right - it.left).value, 1f)
            assertTrue(it.bottom <= header.top)
        }
        list.performScrollToIndex(3)
        val pinnedHeader = compose.onNodeWithTag("detail-section-header").getUnclippedBoundsInRoot()
        assertEquals((header.bottom - header.top).value, (pinnedHeader.bottom - pinnedHeader.top).value, .5f)
        val safeArea = compose.onNodeWithTag("detail-status-bar-background").getUnclippedBoundsInRoot()
        val tab = compose.onNode(hasText("材料", substring = false) and hasClickAction()).getUnclippedBoundsInRoot()
        assertEquals(safeArea.bottom.value, tab.top.value, 1f)
        assertTrue(safeArea.bottom > safeArea.top)
        var safeTopPx = 0
        compose.runOnIdle {
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
            safeTopPx = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
            ).top
        }
        assertEquals(
            safeTopPx / compose.activity.resources.displayMetrics.density,
            (safeArea.bottom - safeArea.top).value,
            1f,
        )
    }

    @Test fun manualTimerAndStepShortcutsWorkAndPauseSurvivesRecreation() {
        compose.onNodeWithText("红烧肉").performClick()
        compose.onNodeWithTag("bottom-cooking-mode").performClick()
        compose.onNodeWithText("计时 半小时至一小时").assertIsDisplayed()
        compose.onNodeWithContentDescription("计时器").performClick()
        compose.onNodeWithText("添加计时器").performClick()
        compose.onNodeWithTag("timer-minutes").performTextReplacement("2")
        compose.onNodeWithText("开始计时").performClick()
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("暂停计时").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError(
                "timers=${container.timers.timers.value}; error=${container.timers.error.value}\n" +
                    compose.onAllNodes(isRoot()).printToString(),
                failure,
            )
        }
        compose.onNodeWithContentDescription("暂停计时").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("已暂停", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("已暂停", substring = true).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        runBlocking { assertEquals(1, container.timers.timers.value.size) }
        compose.onNodeWithContentDescription("计时器").performClick()
        compose.onNodeWithText("已暂停", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("继续计时").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("暂停计时").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("取消计时").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("还没有计时器。可以从步骤下方快速添加。").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("还没有计时器。可以从步骤下方快速添加。").assertIsDisplayed()
    }
}
