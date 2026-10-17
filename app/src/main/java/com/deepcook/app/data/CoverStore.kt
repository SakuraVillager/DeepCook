package com.deepcook.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Stores the cover photo of a user-authored recipe.
 *
 * Photos picked from the gallery are copied into the app's private storage rather than
 * referenced by content URI: the picker's grant is temporary, the user may move or delete the
 * original, and the recipe has to keep its picture across restarts. The copy follows the same
 * convention as the bundled covers — 4:3, 1200x900, JPEG q82 — so every card and detail page
 * crops the same way regardless of where the recipe came from.
 */
interface RecipeCovers {
    suspend fun import(uri: String): String?
    suspend fun delete(cover: String?)
}

class CoverStore(private val context: Context) : RecipeCovers {
    private val covers: File
        get() = File(context.filesDir, COVER_DIR)

    /** @return a `file:` URI string for `Recipe.cover`, or null when the image cannot be read. */
    override suspend fun import(uri: String): String? = withContext(Dispatchers.IO) {
        var source: Bitmap? = null
        var prepared: Bitmap? = null
        var destination: File? = null
        var complete = false
        try {
            source = decode(uri.toUri()) ?: return@withContext null
            val preparedBitmap = fitToCard(source)
            prepared = preparedBitmap
            val folder = covers.apply { mkdirs() }
            val file = File(folder, "cover-${UUID.randomUUID()}.jpg")
            destination = file
            FileOutputStream(file).use { output ->
                if (!preparedBitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) throw IOException("封面无法编码")
                output.fd.sync()
            }
            complete = true
            Uri.fromFile(file).toString()
        } catch (error: IOException) {
            null
        } catch (error: SecurityException) {
            // The picker grant can expire between choosing and reading; treat it as "no image".
            null
        } catch (_: IllegalArgumentException) {
            null
        } finally {
            if (prepared !== source) prepared?.recycle()
            source?.recycle()
            if (!complete) destination?.delete()
        }
    }

    /** Removes a stored cover; ignores anything that is not one of ours. */
    override suspend fun delete(cover: String?) = withContext(Dispatchers.IO) {
        if (cover == null || !cover.startsWith("file:")) return@withContext
        val path = cover.toUri().path ?: return@withContext
        val file = File(path).canonicalFile
        // Only ever delete inside our own directory, never an arbitrary path.
        if (file.parentFile == covers.canonicalFile && file.name.matches(Regex("cover-[a-f0-9-]+\\.jpg"))) {
            if (file.exists() && !file.delete()) throw IOException("封面无法移除")
        }
    }

    /** Decodes with a sample size that keeps the bitmap close to the final size. */
    private fun decode(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096 ||
            bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > 8_000_000
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        return rotate(uri, bitmap)
    }

    /** Applies the EXIF orientation, otherwise a camera photo would land on its side. */
    private fun rotate(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** Center-crops to 4:3 and scales to the size the bundled covers use. */
    private fun fitToCard(bitmap: Bitmap): Bitmap {
        val ratio = TARGET_WIDTH.toFloat() / TARGET_HEIGHT
        val width = bitmap.width
        val height = bitmap.height
        val cropWidth: Int
        val cropHeight: Int
        if (width.toFloat() / height > ratio) {
            cropHeight = height
            cropWidth = (height * ratio).toInt().coerceAtMost(width)
        } else {
            cropWidth = width
            cropHeight = (width / ratio).toInt().coerceAtMost(height)
        }
        val cropX = (width - cropWidth) / 2
        val cropY = (height - cropHeight) / 2
        val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
        val scaled = cropped.scale(TARGET_WIDTH, TARGET_HEIGHT, true)
        if (cropped !== bitmap && cropped !== scaled) cropped.recycle()
        return scaled
    }

    companion object {
        private const val COVER_DIR = "covers"
        private const val TARGET_WIDTH = 1200
        private const val TARGET_HEIGHT = 900
    }
}
