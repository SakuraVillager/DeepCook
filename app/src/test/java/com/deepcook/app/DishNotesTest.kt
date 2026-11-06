package com.deepcook.app

import com.deepcook.app.core.model.DishNote
import com.deepcook.app.feature.notes.DishNoteGroup
import com.deepcook.app.feature.notes.groupNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class DishNotesTest {
    private val titles = mapOf("r1" to "红烧肉", "r2" to "番茄炒蛋")

    @Test fun groupsByDishWithTheMostRecentlyTouchedDishFirst() {
        val notes = listOf(
            DishNote("a", "r1", "少放盐", updatedAt = 10),
            DishNote("b", "r2", "多放糖", updatedAt = 30),
            DishNote("c", "r1", "火小点", updatedAt = 20),
        )
        val groups = groupNotes(notes, titles)
        assertEquals(listOf("r2", "r1"), groups.map { it.recipeId })
        assertEquals(listOf("番茄炒蛋", "红烧肉"), groups.map { it.title })
        assertEquals(listOf("火小点", "少放盐"), groups.last().notes.map { it.text })
    }

    @Test fun aNoteForARetiredRecipeKeepsItsWordingAndStaysVisible() {
        val groups = groupNotes(listOf(DishNote("a", "gone", "少放盐", updatedAt = 5)), titles)
        assertEquals(1, groups.size)
        assertEquals("已移除的菜谱", groups.single().title)
        assertEquals("少放盐", groups.single().notes.single().text)
    }

    @Test fun noNotesMeansNoGroups() {
        assertEquals(emptyList<DishNoteGroup>(), groupNotes(emptyList(), titles))
    }
}
