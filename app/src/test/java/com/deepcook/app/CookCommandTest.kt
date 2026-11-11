package com.deepcook.app

import com.deepcook.app.feature.cooking.CookCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CookCommandTest {
    @Test fun aSpokenTimerBecomesMinutes() {
        assertEquals(10, CookCommand.timerMinutes("计时十分钟"))
        assertEquals(10, CookCommand.timerMinutes("十分钟"))
        assertEquals(5, CookCommand.timerMinutes("定个 5 分钟的闹钟"))
        assertEquals(30, CookCommand.timerMinutes("半小时后提醒我"))
        assertEquals(120, CookCommand.timerMinutes("计时2小时"))
    }

    @Test fun ordinaryQuestionsDoNotStartATimer() {
        assertNull(CookCommand.timerMinutes("炖十分钟够吗"))
        assertNull(CookCommand.timerMinutes("要炖多久"))
        assertNull(CookCommand.timerMinutes("这个熟了吗"))
        assertNull(CookCommand.timerMinutes(""))
    }

    @Test fun absurdDurationsAreIgnored() {
        assertNull(CookCommand.timerMinutes("计时200分钟"))
        assertNull(CookCommand.timerMinutes("计时0分钟"))
    }

    /** The recogniser punctuates its output now; the marks must not turn a command into a question. */
    @Test fun punctuationDoesNotHideATimer() {
        assertEquals(10, CookCommand.timerMinutes("十分钟。"))
        assertEquals(10, CookCommand.timerMinutes("计时十分钟，谢谢。"))
        assertEquals(30, CookCommand.timerMinutes("半小时后提醒我！"))
        assertNull(CookCommand.timerMinutes("炖十分钟够吗？"))
    }
}
