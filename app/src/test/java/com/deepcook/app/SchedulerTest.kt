package com.deepcook.app

import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeStep
import com.deepcook.app.core.model.StepKind
import com.deepcook.app.feature.menu.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {
    /** [steps] pairs are (minutes, kind); both may be null to exercise the fallbacks. */
    private fun dish(id: String, vararg steps: Pair<Int?, StepKind?>) = Recipe(
        id = id,
        title = id,
        description = "",
        categories = listOf("家常菜"),
        servings = "2 人份",
        ingredients = listOf(Ingredient("i0", "材料", "1 个")),
        steps = steps.mapIndexed { index, step ->
            RecipeStep(
                id = "$id-s${index + 1}",
                order = index + 1,
                title = "第 ${index + 1} 步",
                text = "$id 第 ${index + 1} 步",
                minutes = step.first,
                kind = step.second,
            )
        },
    )

    @Test fun threeDishesNeverCookAtTheSameTime() {
        val schedule = Scheduler.schedule(
            listOf(
                dish("a", 10 to StepKind.ACTIVE, 5 to StepKind.ACTIVE),
                dish("b", 8 to StepKind.ACTIVE, 4 to StepKind.ACTIVE),
                dish("c", 6 to StepKind.ACTIVE),
            ),
        )
        val active = schedule.tasks.filter { it.active }.sortedBy { it.offsetMinutes }
        active.zipWithNext().forEach { (first, second) ->
            assertTrue("${first.stepId} 与 ${second.stepId} 撞车", first.endsAt <= second.offsetMinutes)
        }
        assertEquals(setOf("a", "b", "c"), schedule.tasks.map { it.recipeId }.toSet())
    }

    @Test fun aPassiveWaitLetsAnotherDishCook() {
        val schedule = Scheduler.schedule(
            listOf(
                dish("a", 5 to StepKind.ACTIVE, 30 to StepKind.PASSIVE, 5 to StepKind.ACTIVE),
                dish("b", 20 to StepKind.ACTIVE),
            ),
        )
        val wait = schedule.task("a", "a-s2")!!
        val other = schedule.task("b", "b-s1")!!
        val after = schedule.task("a", "a-s3")!!
        // B's active work fits inside A's 30-minute wait...
        assertTrue(other.offsetMinutes >= wait.offsetMinutes)
        assertTrue(other.endsAt <= wait.endsAt)
        // ...but A's own next step still waits for the marinade.
        assertTrue(after.offsetMinutes >= wait.endsAt)
    }

    @Test fun eachDishKeepsItsOwnStepOrder() {
        val recipes = listOf(
            dish("a", 10 to StepKind.ACTIVE, 30 to StepKind.PASSIVE, 5 to StepKind.ACTIVE),
            dish("b", 8 to StepKind.ACTIVE, 4 to StepKind.ACTIVE),
        )
        val schedule = Scheduler.schedule(recipes)
        recipes.forEach { recipe ->
            val placed = schedule.tasks.filter { it.recipeId == recipe.id }.sortedBy { it.offsetMinutes }
            assertEquals(recipe.steps.size, placed.size)
            placed.zipWithNext().forEach { (first, second) ->
                assertTrue("${first.stepId} 之后不能先做 ${second.stepId}", first.endsAt <= second.offsetMinutes)
            }
        }
    }

    @Test fun unknownTimingUsesConservativeDefaults() {
        val schedule = Scheduler.schedule(listOf(dish("a", null to null, null to StepKind.PASSIVE)))
        assertEquals(Scheduler.DEFAULT_ACTIVE_MINUTES, schedule.tasks[0].minutes)
        assertEquals(StepKind.ACTIVE, schedule.tasks[0].kind)
        assertEquals(Scheduler.DEFAULT_PASSIVE_MINUTES, schedule.tasks[1].minutes)
        assertEquals(StepKind.PASSIVE, schedule.tasks[1].kind)
    }

    @Test fun aFeasibleLockIsKept() {
        val schedule = Scheduler.schedule(listOf(dish("a", 5 to StepKind.ACTIVE)), locks = mapOf("a-s1" to 10))
        assertEquals(10, schedule.task("a", "a-s1")!!.offsetMinutes)
        assertTrue(schedule.conflicts.isEmpty())
    }

    @Test fun aLockBeforeItsOwnPreviousStepEndsIsReportedAndShifted() {
        // Locking step 2 at 0 cannot work: step 1 itself takes 20 minutes.
        val schedule = Scheduler.schedule(
            listOf(dish("a", 20 to StepKind.ACTIVE, 5 to StepKind.ACTIVE)),
            locks = mapOf("a-s2" to 0),
        )
        val second = schedule.task("a", "a-s2")!!
        assertEquals(1, schedule.conflicts.size)
        assertEquals(0, schedule.conflicts.single().requested)
        assertEquals(20, second.offsetMinutes)
    }

    @Test fun aSingleDishIsJustItsOwnTimeline() {
        val schedule = Scheduler.schedule(listOf(dish("a", 5 to StepKind.ACTIVE, 10 to StepKind.PASSIVE)))
        assertEquals(listOf(0, 5), schedule.tasks.map { it.offsetMinutes })
        assertEquals(15, schedule.totalMinutes)
        assertEquals(5, schedule.activeMinutes)
    }

    @Test fun theSameInputGivesTheSamePlan() {
        val recipes = listOf(
            dish("a", 10 to StepKind.ACTIVE, 30 to StepKind.PASSIVE, 5 to StepKind.ACTIVE),
            dish("b", 8 to StepKind.ACTIVE, 4 to StepKind.ACTIVE),
        )
        assertEquals(Scheduler.schedule(recipes), Scheduler.schedule(recipes))
    }

    @Test fun everyTaskPointsBackAtItsStep() {
        val recipes = listOf(dish("a", 10 to StepKind.ACTIVE), dish("b", 8 to StepKind.PASSIVE))
        val schedule = Scheduler.schedule(recipes)
        schedule.tasks.forEach { task ->
            val recipe = recipes.first { it.id == task.recipeId }
            assertTrue(recipe.steps.any { it.id == task.stepId })
        }
    }

    @Test fun anEmptyMenuHasNothingToDo() {
        val schedule = Scheduler.schedule(emptyList())
        assertTrue(schedule.tasks.isEmpty())
        assertEquals(0, schedule.totalMinutes)
    }
}
