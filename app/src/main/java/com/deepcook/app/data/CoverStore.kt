package com.deepcook.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
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
class CoverStore(private val context: Context) {
    private val covers: File
        get() = File(context.filesDir, COVER_DIR)

    /** @return a `file:` URI string for `Recipe.cover`, or null when the image cannot be read. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val source = decode(uri) ?: return@withContext null
            val prepared = fitToCard(source)
            val folder = covers.apply { mkdirs() }
            val file = File(folder, "cover-${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { output -> prepared.compress(Bitmap.CompressFormat.JPEG, 82, output) }
            if (prepared !== source) prepared.recycle()
            source.recycle()
            Uri.fromFile(file).toString()
        } catch (error: IOException) {
            null
        } catch (error: SecurityException) {
            // The picker grant can expire between choosing and reading; treat it as "no image".
            null
        }
    }

    /** Removes a stored cover; ignores anything that is not one of ours. */
    suspend fun delete(cover: String?) = withContext(Dispatchers.IO) {
        if (cover == null || !cover.startsWith("file:")) return@withContext
        val path = Uri.parse(cover).path ?: return@withContext
        val file = File(path).canonicalFile
        // Only ever delete inside our own directory, never an arbitrary path.
        if (file.parentFile == covers.canonicalFile) file.delete()
    }

    /** Decodes with a sample size that keeps the bitmap close to the final size. */
    private fun decode(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= TARGET_WIDTH &&
            bounds.outHeight / (sample * 2) >= TARGET_HEIGHT
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
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
        val scaled = Bitmap.createScaledBitmap(cropped, TARGET_WIDTH, TARGET_HEIGHT, true)
        if (cropped !== bitmap && cropped !== scaled) cropped.recycle()
        return scaled
    }

    companion object {
        private const val COVER_DIR = "covers"
        private const val TARGET_WIDTH = 1200
        private const val TARGET_HEIGHT = 900
    }
}
