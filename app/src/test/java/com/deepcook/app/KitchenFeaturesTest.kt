package com.deepcook.app

import com.deepcook.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class KitchenFeaturesTest {
    @Test fun automaticTimersIgnoreShortDurationsAfterMergingRanges() {
        assertTrue(StepDurations.parse("搅拌5秒，再停9 seconds").isEmpty())
        assertEquals(listOf(10L, 15L, 60L), StepDurations.parse("等10秒，5–15秒，5秒到1分钟").map { it.maximumSeconds })
        assertTrue(StepDurations.parse("搅拌1–9秒").isEmpty())
    }
    @Test fun explicitDurationsRecogniseChineseRangesFractionsAndCompoundUnits() {
        assertEquals(listOf(900L, 30L, 5400L), StepDurations.parse("煮十五分钟，再煎30秒，腌制一个小时半").map { it.maximumSeconds })
        val range = StepDurations.parse("小火炖 3–5 分钟").single()
        assertEquals(180L, range.minimumSeconds)
        assertEquals(300L, range.maximumSeconds)
        assertTrue(range.isRange)
        assertEquals(5400L, StepDurations.parse("1小时30分钟").single().maximumSeconds)
        assertEquals(1800L, StepDurations.parse("静置1/2小时").single().maximumSeconds)
        assertEquals(180L, StepDurations.parse("炒两三分钟").single().maximumSeconds)
        val hours = StepDurations.parse("冷冻半小时至一小时").single()
        assertEquals(1800L, hours.minimumSeconds)
        assertEquals(3600L, hours.maximumSeconds)
        assertEquals("半小时至一小时", hours.text)
    }

    @Test fun cookingDimensionsAndDonenessNeverBecomeTimers() {
        assertTrue(StepDurations.parse("切成 2cm，放 180°C 烤箱，炒至七分熟，加 500ml 水和 20g 盐。").isEmpty())
        assertTrue(StepDurations.parse("煮至熟透，不提供时间。").isEmpty())
        assertTrue(StepDurations.parse("10到5分钟、0秒、999小时").isEmpty())
    }

    @Test fun englishUnitsCanBeFollowedByChineseWithoutFalseMatchingWords() {
        assertEquals(listOf(1200L, 10L, 3600L), StepDurations.parse("烤20min后翻面，搅拌10s，再等1h。").map { it.maximumSeconds })
        assertEquals(1800L, StepDurations.parse("bake for 30 minutes at 180 degrees").single().maximumSeconds)
        assertTrue(StepDurations.parse("20 minced vegetables").isEmpty())
    }

    @Test fun elapsedClockSurvivesWallClockChangesAndRebootUsesSavedWallDeadline() {
        val timer = CookingTimer("a", "r", "菜", null, "测试", 60_000, 160_000, 70_000, 1, 60_000, TimerStatus.RUNNING)
        assertEquals(50_000L, timer.remaining(999_999, 20_000, 1))
        assertEquals(40_000L, timer.remaining(120_000, 1_000, 2))
        assertEquals(0L, timer.remaining(170_000, 1_000, 2))
        assertEquals(60_000L, timer.copy(status = TimerStatus.PAUSED).remaining(999_999, 999_999, 2))
        assertEquals(0L, timer.copy(status = TimerStatus.FINISHED).remaining(0, 0, 1))
    }

    @Test fun countdownAndCompactFavoriteCountsRemainReadable() {
        assertEquals("00:01", countdownLabel(1))
        assertEquals("01:00", countdownLabel(60_000))
        assertEquals("1:00:00", countdownLabel(3_600_000))
        assertEquals("0", compactCount(-1))
        assertEquals("12", compactCount(12))
        assertEquals("1.2k", compactCount(1200))
        assertEquals("1.2w", compactCount(12000))
    }
}
