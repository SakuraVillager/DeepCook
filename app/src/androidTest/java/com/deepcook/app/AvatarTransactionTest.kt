package com.deepcook.app

import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepcook.app.app.AppViewModel
import com.deepcook.app.core.model.*
import com.deepcook.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AvatarTransactionTest {
    private class MemoryStore : DataStore<UserData> {
        override val data = MutableStateFlow(UserData(profile = UserProfile(avatar = "old-avatar")))
        var failing = false
        override suspend fun updateData(transform: suspend (t: UserData) -> UserData): UserData {
            if (failing) throw IOException("disk full")
            return transform(data.value).also { data.value = it }
        }
    }

    private class Photos(private val store: MemoryStore) : Avatars {
        val removed = mutableListOf<String?>()
        override suspend fun decode(uri: String): Bitmap? = null
        override suspend fun save(bitmap: Bitmap): String = "new-avatar"
        override suspend fun delete(avatar: String?) {
            if (avatar == "old-avatar") assertEquals("new-avatar", store.data.value.profile.avatar)
            removed += avatar
        }
    }

    private fun viewModel(store: MemoryStore, photos: Avatars): AppViewModel = AppViewModel(
        recipes = object : RecipeRepository { override suspend fun recipes() = emptyList<Recipe>() },
        userData = LocalUserDataRepository(store),
        userRecipes = object : UserRecipeStore {
            override val recipes = MutableStateFlow(emptyList<Recipe>())
            override suspend fun save(recipe: Recipe) {}
            override suspend fun delete(id: String) {}
        },
        covers = CoverStore(ApplicationProvider.getApplicationContext()),
        avatars = photos,
    )

    @Test fun failedProfileWriteRemovesOnlyNewFileAndKeepsOriginalAvatar() = runBlocking {
        val data = MemoryStore().apply { failing = true }
        val photos = Photos(data)
        val vm = viewModel(data, photos)
        val owner = ViewModelStore().apply { put("avatar", vm) }
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            vm.saveAvatar(bitmap).join()
            assertEquals("old-avatar", data.data.value.profile.avatar)
            assertEquals(listOf("new-avatar"), photos.removed)
            assertFalse(vm.state.value.avatarSaving)
        } finally { owner.clear(); bitmap.recycle() }
    }

    @Test fun successfulProfileWriteCommitsReferenceBeforeRemovingOldFile() = runBlocking {
        val data = MemoryStore()
        val photos = Photos(data)
        val vm = viewModel(data, photos)
        val owner = ViewModelStore().apply { put("avatar", vm) }
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            vm.saveAvatar(bitmap).join()
            assertEquals("new-avatar", data.data.value.profile.avatar)
            assertEquals(listOf("old-avatar"), photos.removed)
        } finally { owner.clear(); bitmap.recycle() }
    }

    @Test fun stagingAnotherAvatarDoesNotDeleteExistingFile() = runBlocking {
        val store = AvatarStore(ApplicationProvider.getApplicationContext())
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val old = store.save(bitmap)
        val next = store.save(bitmap)
        try {
            assertNotNull(old)
            assertNotNull(next)
            assertTrue(File(android.net.Uri.parse(old).path!!).exists())
            assertTrue(File(android.net.Uri.parse(next).path!!).exists())
        } finally { store.delete(old); store.delete(next); bitmap.recycle() }
    }
}
