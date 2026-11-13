package com.deepcook.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepcook.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KitchenDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun corruptedDatabaseIsReportedWithoutReplacingTheOriginalFile() {
        val name = "corrupt-kitchen-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val original = "not a database - keep this file".repeat(10).toByteArray()
        path.writeBytes(original)
        val database = KitchenDatabase(context, name)
        try {
            assertTrue(runCatching { database.readableDatabase }.isFailure)
            assertTrue(path.exists())
            assertArrayEquals(original, path.readBytes())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun examplesLocalRatingAndPostsSurviveDatabaseReopen() = runBlocking {
        val name = "kitchen-test-${UUID.randomUUID()}.db"
        var database = KitchenDatabase(context, name)
        try {
            var store = KitchenStore(database)
            val original = store.observe("braised-pork").first()
            assertNull(original.nutrition)
            assertEquals(3, original.ratings.size)
            assertTrue(original.posts.all { it.sample })
            assertTrue(original.favoriteExamples > 0)
            store.rate("braised-pork", 10)
            store.rate("braised-pork", 7)
            val id = store.post("braised-pork", false, "本机持久化评论", null)
            val work = store.post("braised-pork", true, "我的作业", "file:///example.jpg")
            database.close()
            database = KitchenDatabase(context, name)
            store = KitchenStore(database)
            val restored = store.observe("braised-pork").first()
            assertEquals(4, restored.ratings.size)
            assertEquals(7, restored.ownRating)
            assertEquals(2, restored.posts.count { !it.sample })
            assertEquals("file:///example.jpg", store.deletePost(work))
            store.deletePost(restored.posts.first { it.sample }.id)
            assertEquals(2, store.observe("braised-pork").first().posts.count { it.sample })
            store.deletePost(id)
            assertTrue(store.observe("braised-pork").first().posts.all { it.sample })
            assertNull(store.observe("user-unknown").first().nutrition)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun upgradeRefreshesSeedPresentationWithoutChangingPersonalContent() = runBlocking {
        val name = "kitchen-migration-${UUID.randomUUID()}.db"
        var database = KitchenDatabase(context, name)
        try {
            val store = KitchenStore(database)
            val id = store.post("braised-pork", false, "我自己的评论", null)
            store.rate("braised-pork", 9)
            val db = database.writableDatabase
            db.execSQL("UPDATE posts SET author='示例用户 · 小禾', text='示例评论：下次想试试这个做法。' WHERE sample=1 AND submission=0")
            db.execSQL("UPDATE posts SET author='示例用户 · 小禾' WHERE id=?", arrayOf(id))
            db.version = 1
            database.close()
            database = KitchenDatabase(context, name)
            val restored = KitchenStore(database).observe("braised-pork").first()
            assertEquals(9, restored.ownRating)
            assertEquals("示例用户 · 小禾", restored.posts.first { it.id == id }.author)
            assertEquals("我自己的评论", restored.posts.first { it.id == id }.text)
            val seeded = restored.posts.first { it.sample && !it.submission }
            assertEquals("小禾", seeded.author)
            assertEquals("下次想试试这个做法。", seeded.text)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

}

internal fun grantNotificationPermission(context: Context) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    if (android.os.Build.VERSION.SDK_INT >=
        33
    ) {
        automation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
