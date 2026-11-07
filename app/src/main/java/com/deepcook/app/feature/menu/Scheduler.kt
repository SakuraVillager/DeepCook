package com.deepcook.app.feature.menu

import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.StepKind

/**
 * One step placed on a cooking session's relative timeline.
 *
 * [offsetMinutes] is minutes from the moment the session starts, not a wall-clock time; the UI
 * adds the start time when it shows "19:30 下锅". The pair ([recipeId], [stepId]) always points
 * back at the original step, so every row in a merged list can be traced to its source.
 */
data class CookTask(
    val recipeId: String,
    val stepId: String,
    val text: String,
    val minutes: Int,
    val kind: StepKind,
    val offsetMinutes: Int,
) {
    val active: Boolean get() = kind == StepKind.ACTIVE
    val endsAt: Int get() = offsetMinutes + minutes
}

/** A lock the user asked for that had to move; [actual] is where the step really starts. */
data class ScheduleConflict(val stepId: String, val requested: Int, val actual: Int)

data class Schedule(
    val tasks: List<CookTask>,
    val conflicts: List<ScheduleConflict> = emptyList(),
    val totalMinutes: Int = tasks.maxOfOrNull { it.endsAt } ?: 0,
    val activeMinutes: Int = tasks.filter { it.active }.sumOf { it.minutes },
) {
    /** The placed task for one step, or null when that dish was not part of this plan. */
    fun task(recipeId: String, stepId: String): CookTask? =
        tasks.firstOrNull { it.recipeId == recipeId && it.stepId == stepId }
}

/**
 * Places two or three dishes on one timeline.
 *
 * The only hard rule is the one a kitchen enforces: **one ACTIVE step at a time**. A PASSIVE step
 * (marinating, simmering) still blocks the next step of its own dish, but it does not occupy the
 * cook, so another dish's ACTIVE work runs during the wait. Nothing here asks a model; the plan is
 * deterministic for the same input and the same locks.
 *
 * Locks are honoured when they are feasible. A lock that would start before its own previous step
 * ended, or on top of another dish's ACTIVE work, is reported in [Schedule.conflicts] and the step
 * starts at the earliest feasible minute instead of silently breaking the kitchen rule.
 */
object Scheduler {
    /** Used only for user recipes that never got timing data; conservative, never zero. */
    const val DEFAULT_ACTIVE_MINUTES = 5
    const val DEFAULT_PASSIVE_MINUTES = 15

    private data class Interval(val start: Int, val end: Int)

    private class DishState(val index: Int, val recipe: Recipe, var next: Int = 0, var ready: Int = 0)

    private data class Candidate(
        val state: DishState,
        val stepIndex: Int,
        val stepId: String,
        val text: String,
        val kind: StepKind,
        val minutes: Int,
        val earliest: Int,
        val lock: Int?,
    ) {
        val start: Int get() = lock ?: earliest
    }

    fun schedule(recipes: List<Recipe>, locks: Map<String, Int> = emptyMap()): Schedule {
        val states = recipes.mapIndexed { index, recipe -> DishState(index, recipe) }
        val busy = mutableListOf<Interval>()
        val tasks = mutableListOf<CookTask>()
        val conflicts = mutableListOf<ScheduleConflict>()

        while (true) {
            val candidates = states.mapNotNull { state ->
                val step = state.recipe.steps.getOrNull(state.next) ?: return@mapNotNull null
                val kind = step.kind ?: StepKind.ACTIVE
                val minutes = step.minutes?.takeIf { it > 0 }
                    ?: if (kind == StepKind.PASSIVE) DEFAULT_PASSIVE_MINUTES else DEFAULT_ACTIVE_MINUTES
                val earliest = if (kind == StepKind.ACTIVE) firstFree(state.ready, minutes, busy) else state.ready
                Candidate(
                    state = state,
                    stepIndex = state.next,
                    stepId = step.id,
                    text = step.text.take(40),
                    kind = kind,
                    minutes = minutes,
                    earliest = earliest,
                    lock = locks[step.id],
                )
            }
            if (candidates.isEmpty()) break

            // Earliest start wins; the dish order breaks ties so the plan is deterministic.
            val chosen = candidates.minWith(compareBy<Candidate>({ it.start }, { it.state.index }))
            val requested = chosen.lock
            val start = if (requested == null) {
                chosen.earliest
            } else {
                val feasible = requested >= chosen.state.ready &&
                    (chosen.kind != StepKind.ACTIVE || !overlaps(requested, chosen.minutes, busy))
                if (feasible) {
                    requested
                } else {
                    conflicts += ScheduleConflict(chosen.stepId, requested, chosen.earliest)
                    chosen.earliest
                }
            }

            tasks += CookTask(
                recipeId = chosen.state.recipe.id,
                stepId = chosen.stepId,
                text = chosen.text,
                minutes = chosen.minutes,
                kind = chosen.kind,
                offsetMinutes = start,
            )
            if (chosen.kind == StepKind.ACTIVE) busy += Interval(start, start + chosen.minutes)
            chosen.state.next = chosen.stepIndex + 1
            chosen.state.ready = start + chosen.minutes
        }

        return Schedule(
            tasks = tasks.sortedWith(compareBy({ it.offsetMinutes }, { it.recipeId })),
            conflicts = conflicts,
        )
    }

    /** The first minute at or after [ready] where a whole [minutes]-long gap is free. */
    private fun firstFree(ready: Int, minutes: Int, busy: List<Interval>): Int {
        var time = ready
        for (interval in busy.sortedBy { it.start }) {
            if (interval.end <= time) continue
            if (interval.start >= time + minutes) return time
            time = interval.end
        }
        return time
    }

    private fun overlaps(start: Int, minutes: Int, busy: List<Interval>): Boolean =
        busy.any { interval -> start < interval.end && interval.start < start + minutes }
}
