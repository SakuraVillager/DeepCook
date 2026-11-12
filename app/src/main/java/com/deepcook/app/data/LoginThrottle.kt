package com.deepcook.app.data

import com.deepcook.app.core.model.AuthRules

/**
 * Counts wrong passwords in memory and locks a number for [AuthRules.LOCK_SECONDS] after
 * [AuthRules.MAX_FAILURES] misses. Deliberately not persisted: a restart clears the lockout, which
 * is fine for a local account and keeps `account.json` free of attack bookkeeping.
 */
class LoginThrottle(
    private val maxFailures: Int = AuthRules.MAX_FAILURES,
    private val lockMillis: Long = AuthRules.LOCK_SECONDS * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Attempts(val failures: Int, val lockedUntil: Long)

    private val attempts = mutableMapOf<String, Attempts>()

    /** Seconds left on the lock, or 0 when the number may try again. */
    @Synchronized
    fun lockRemainingSeconds(phone: String): Int {
        val entry = attempts[phone] ?: return 0
        val remaining = entry.lockedUntil - clock()
        return if (remaining <= 0L) 0 else ((remaining + 999L) / 1000L).toInt()
    }

    @Synchronized
    fun recordFailure(phone: String) {
        val failures = (attempts[phone]?.failures ?: 0) + 1
        attempts[phone] = if (failures >= maxFailures) {
            Attempts(0, clock() + lockMillis)
        } else {
            Attempts(failures, 0L)
        }
    }

    @Synchronized
    fun clear(phone: String) {
        attempts.remove(phone)
    }
}
