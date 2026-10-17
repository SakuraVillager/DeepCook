package com.deepcook.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.deepcook.app.data.CoverStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CoverStoreTest {
    @Test fun importsMirroredPhotoAndDeletesOnlyItsPrivateCopy() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "cover-test-${System.nanoTime()}.jpg")
        val store = CoverStore(context)
        var imported: String? = null
        try {
            val bitmap = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            Canvas(bitmap).drawRect(120f, 0f, 240f, 180f, Paint().apply { color = Color.BLUE })
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            bitmap.recycle()
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_FLIP_HORIZONTAL.toString())
                saveAttributes()
            }
            imported = store.import(Uri.fromFile(source).toString())
            assertNotNull(imported)
            val file = File(Uri.parse(imported).path!!)
            val result = BitmapFactory.decodeFile(file.absolutePath)
            assertEquals(1200, result.width)
            assertEquals(900, result.height)
            val left = result.getPixel(100, 450)
            assertTrue("EXIF mirror must be applied", Color.blue(left) > Color.red(left))
            result.recycle()
            store.delete(Uri.fromFile(source).toString())
            assertTrue("Source file outside covers must not be deleted", source.exists())
            store.delete(imported)
            assertFalse(file.exists())
        } finally {
            store.delete(imported)
            source.delete()
        }
    }

    @Test fun unreadableImageDoesNotCreateAPartialCover() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val invalid = File(context.cacheDir, "invalid-cover-${System.nanoTime()}.jpg")
        val covers = File(context.filesDir, "covers")
        val before = covers.list()?.toSet().orEmpty()
        try {
            invalid.writeText("not an image")
            assertNull(CoverStore(context).import(Uri.fromFile(invalid).toString()))
            assertEquals(before, covers.list()?.toSet().orEmpty())
        } finally {
            invalid.delete()
        }
    }
}
