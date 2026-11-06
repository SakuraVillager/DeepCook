package com.deepcook.app

import androidx.datastore.core.DataStoreFactory
import com.deepcook.app.core.model.*
import com.deepcook.app.data.LocalUserDataRepository
import com.deepcook.app.data.UserDataSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class UserDataRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun recentDeduplicatesAndCapsAtFiftyWithoutChangingFavorites() {
        var data = UserData().withFavorite("saved", true, 1)
        repeat(60) { data = data.withVisit("recipe-$it", it.toLong()) }
        assertEquals(50, data.recent.size)
        data = data.withVisit("recipe-20", 100)
        assertEquals("recipe-20", data.recent.first().recipeId)
        assertEquals(1, data.recent.count { it.recipeId == "recipe-20" })
        assertEquals("saved", data.favorites.single().recipeId)
    }

    @Test fun favoritesOrderAndUndoKeepOriginalTimestamp() {
        var data = UserData().withFavorite("a", true, 10).withFavorite("b", true, 20)
        assertEquals(listOf("b", "a"), data.favorites.map { it.recipeId })
        data = data.withFavorite("a", false, 30).withFavorite("a", true, 10)
        assertEquals(listOf("b", "a"), data.favorites.map { it.recipeId })
    }

    @Test fun serializerPreservesEveryPreference() = runBlocking {
        val original = UserData(
            preferences = UserPreferences(ThemeMode.DARK, ColorSource.DYNAMIC, ReadingSize.EXTRA_LARGE, false, true),
        ).withFavorite("a", true, 11).withVisit("b", 12)
        val output = ByteArrayOutputStream()
        UserDataSerializer.writeTo(original, output)
        assertEquals(original, UserDataSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
    }

    @Test fun serializerPreservesTheProfile() = runBlocking {
        val original = UserData(
            profile = UserProfile("小明", "file:/avatars/avatar-1.jpg", setOf("辣", "鲜"), setOf("香菜")),
        )
        val output = ByteArrayOutputStream()
        UserDataSerializer.writeTo(original, output)
        assertEquals(original, UserDataSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
    }

    @Test fun aFileWrittenBeforeTheProfileExistedStillLoads() = runBlocking {
        // 0.3.0 wrote no "profile" key; the default must fill it in instead of failing the read.
        val legacy = """{"favorites":[{"recipeId":"a","timestamp":11}],"recent":[],"preferences":{}}"""
        val data = UserDataSerializer.readFrom(ByteArrayInputStream(legacy.toByteArray()))
        assertEquals(UserProfile(), data.profile)
        assertEquals("a", data.favorites.single().recipeId)
    }

    @Test fun invalidDataIsReportedWithoutSilentlyResettingCollections() = runBlocking {
        try {
            UserDataSerializer.readFrom(ByteArrayInputStream("invalid-json".toByteArray()))
            fail("Corrupt data must be reported")
        } catch (_: androidx.datastore.core.CorruptionException) { /* Expected: never replace the file. */ }
    }

    @Test fun dishNotesStayNewestFirstAndReplaceTheSameWording() {
        val data = UserData()
            .withNote(DishNote("a", "r1", "少放盐", updatedAt = 1))
            .withNote(DishNote("b", "r1", "火小点", updatedAt = 2))
            .withNote(DishNote("c", "r2", "多放辣", updatedAt = 3))
        assertEquals(listOf("火小点", "少放盐"), data.notesFor("r1").map { it.text })
        val replaced = data.withNote(DishNote("d", "r1", "少放盐", raw = "咸了", updatedAt = 4))
        assertEquals(3, replaced.dishNotes.size)
        assertEquals("d", replaced.notesFor("r1").first().id)
        assertEquals("少放盐", replaced.notesFor("r1").first().text)
        assertTrue(replaced.withoutNote("d").notesFor("r1").none { it.text == "少放盐" })
    }

    @Test fun serializerPreservesDishNotes() = runBlocking {
        val original = UserData().withNote(
            DishNote("n1", "recipe-1", "下次少放盐", raw = "有点咸", updatedAt = 9, source = NoteSource.AI),
        )
        val output = ByteArrayOutputStream()
        UserDataSerializer.writeTo(original, output)
        assertEquals(original, UserDataSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
    }

    @Test fun aFileWrittenBeforeNotesExistedStillLoads() = runBlocking {
        val legacy = """{"favorites":[],"recent":[],"preferences":{}}"""
        val data = UserDataSerializer.readFrom(ByteArrayInputStream(legacy.toByteArray()))
        assertTrue(data.dishNotes.isEmpty())
    }

    @Test fun editingANoteKeepsItsIdAndBecomesTheUsersOwnWording() {
        val data = UserData().withNote(
            DishNote("a", "r1", "少放一点盐", raw = "有点咸", updatedAt = 1, source = NoteSource.AI),
        )
        val edited = data.withEditedNote("a", "  少放盐  ", 9)
        val note = edited.dishNotes.single()
        assertEquals("a", note.id)
        assertEquals("r1", note.recipeId)
        assertEquals("少放盐", note.text)
        assertEquals(9, note.updatedAt)
        assertEquals(NoteSource.USER, note.source)
        // A blank or unknown edit changes nothing rather than deleting the note.
        assertEquals(data, data.withEditedNote("a", "   ", 9))
        assertEquals(data, data.withEditedNote("missing", "随便写", 9))
    }
}
