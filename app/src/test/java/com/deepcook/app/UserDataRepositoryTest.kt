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
}
