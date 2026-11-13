package com.deepcook.app

import com.deepcook.app.core.model.PasswordPolicy
import com.deepcook.app.core.model.PhoneNumber
import com.deepcook.app.data.LoginThrottle
import com.deepcook.app.data.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure rules behind the login form: no Android APIs, so they run as JVM unit tests. */
class AuthRulesTest {
    @Test fun normalizesWhatPeopleActuallyType() {
        assertEquals("13800138000", PhoneNumber.normalize("+86 138-0013-8000"))
        assertEquals("13800138000", PhoneNumber.normalize("8613800138000"))
        assertEquals("13800138000", PhoneNumber.normalize("138 0013 8000"))
    }

    @Test fun acceptsOnlyElevenDigitsStartingWithOneThreeToNine() {
        assertTrue(PhoneNumber.isValid("13800138000"))
        assertFalse(PhoneNumber.isValid("12345678901"))
        assertFalse(PhoneNumber.isValid("1380013800"))
        assertFalse(PhoneNumber.isValid("138001380001"))
    }

    @Test fun masksAllButPrefixAndLastFour() {
        assertEquals("138****8000", PhoneNumber.masked("13800138000"))
        assertEquals("未登录", PhoneNumber.masked("未登录"))
    }

    @Test fun passwordsMatchTheCloudSixTo128CodePointContract() {
        assertEquals("至少 6 位", PasswordPolicy.problemOf("12345"))
        assertEquals("不超过 128 位", PasswordPolicy.problemOf("a".repeat(129)))
        assertNull(PasswordPolicy.problemOf("123456"))
        assertNull(PasswordPolicy.problemOf("abcdef"))
        assertNull(PasswordPolicy.problemOf("a b c d"))
        assertNull(PasswordPolicy.problemOf("😀".repeat(6)))
        assertEquals("至少 6 位", PasswordPolicy.problemOf("😀".repeat(3)))
    }

    @Test fun hashIsSaltedAndOnlyTheRightPasswordVerifies() {
        val salt = PasswordHasher.newSalt()
        val otherSalt = PasswordHasher.newSalt()
        assertNotEquals(salt, otherSalt)
        val hash = PasswordHasher.hash("cook1234", salt, 1_000)
        assertTrue(PasswordHasher.verify("cook1234", salt, 1_000, hash))
        assertFalse(PasswordHasher.verify("cook12345", salt, 1_000, hash))
        assertFalse("a different salt must not verify the same password", PasswordHasher.verify("cook1234", otherSalt, 1_000, hash))
    }

    @Test fun locksAfterFiveMissesAndReleasesWhenTheClockPasses() {
        var time = 0L
        val throttle = LoginThrottle(maxFailures = 5, lockMillis = 60_000L, clock = { time })
        repeat(4) { throttle.recordFailure("13800138000") }
        assertEquals(0, throttle.lockRemainingSeconds("13800138000"))
        throttle.recordFailure("13800138000")
        assertEquals(60, throttle.lockRemainingSeconds("13800138000"))
        time = 30_000
        assertEquals(30, throttle.lockRemainingSeconds("13800138000"))
        time = 60_001
        assertEquals(0, throttle.lockRemainingSeconds("13800138000"))
        throttle.clear("13800138000")
        assertEquals(0, throttle.lockRemainingSeconds("13800138000"))
    }
}
