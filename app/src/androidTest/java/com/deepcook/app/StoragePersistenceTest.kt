package com.deepcook.app

import androidx.datastore.core.DataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.deepcook.app.core.model.ThemeMode
import com.deepcook.app.core.model.UserPreferences
import com.deepcook.app.data.LocalUserDataRepository
import com.deepcook.app.data.UserDataSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class StoragePersistenceTest {
    @Test fun persistsAcrossStoreRecreationAndClearsOnlyRequestedData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "test-user-${System.nanoTime()}.json")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var repository = LocalUserDataRepository(DataStoreFactory.create(UserDataSerializer, scope = scope) { file })
        try {
            repository.setFavorite("a", true, 7)
            repository.recordVisit("a")
            repository.updatePreferences { it.copy(themeMode = ThemeMode.DARK) }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository = LocalUserDataRepository(DataStoreFactory.create(UserDataSerializer, scope = scope) { file })
            assertEquals("a", repository.data.first().favorites.single().recipeId)
            assertEquals(ThemeMode.DARK, repository.data.first().preferences.themeMode)
            repository.clearRecent()
            assertTrue(repository.data.first().recent.isEmpty())
            assertEquals(1, repository.data.first().favorites.size)
            repository.recordVisit("a")
            repository.resetPreferences()
            assertEquals(UserPreferences(), repository.data.first().preferences)
            assertEquals(1, repository.data.first().recent.size)
            repository.clearFavorites()
            assertTrue(repository.data.first().favorites.isEmpty())
            assertEquals(1, repository.data.first().recent.size)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
