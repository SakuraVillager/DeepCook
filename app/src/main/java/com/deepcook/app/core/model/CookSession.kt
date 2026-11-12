package com.deepcook.app.core.model

import kotlinx.serialization.Serializable

/**
 * A countdown started from the cooking screen or the reader. [endsAt] is wall-clock time, not a
 * duration, so a process restart or a night in the background still ends the timer when it should.
 *
 * Wall-clock time also means a reboot needs no repair step: unlike `elapsedRealtime`, the deadline
 * stays valid across power cycles.
 */
@Serializable
data class CookTimer(
    val id: String,
    val label: String,
    /** Epoch millis when this timer rings. Ignored while [pausedRemainingMs] is set. */
    val endsAt: Long,
    /** The length the user picked, kept so the notification and the row can say "10 分钟". */
    val minutes: Int,
    /** Which step started it, so the reader can show it under that step. */
    val stepId: String? = null,
    /** Remaining millis while paused; null means the timer is running. */
    val pausedRemainingMs: Long? = null,
) {
    val paused: Boolean get() = pausedRemainingMs != null

    /** Millis left right now; a paused timer keeps the value it was paused at. */
    fun remainingMs(now: Long): Long = pausedRemainingMs ?: (endsAt - now).coerceAtLeast(0)
}

/**
 * One dish being cooked. Only the most recent session is kept: opening another recipe starts
 * over, and "完成" clears the file.
 */
@Serializable
data class CookSession(
    val recipeId: String,
    /** Copied at start so the ongoing notification can name the dish without reading assets. */
    val recipeTitle: String = "",
    val stepIndex: Int = 0,
    val startedAt: Long,
    val timers: List<CookTimer> = emptyList(),
) {
    /** Timers that have not rung yet and are not paused, nearest first. */
    fun pendingTimers(now: Long): List<CookTimer> =
        timers.filter { !it.paused && it.endsAt > now }.sortedBy { it.endsAt }
}

/** DataStore wrapper so "nothing is cooking" is expressible without a sentinel recipe id. */
@Serializable
data class CookSessionFile(val session: CookSession? = null)
