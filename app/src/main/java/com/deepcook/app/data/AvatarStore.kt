package com.deepcook.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Stores the profile picture.
 *
 * The cropped square is copied into the app's private storage instead of keeping the picker URI:
 * that grant is temporary and the user may delete the original photo. Unlike a cover there is no
 * fixed aspect ratio to impose, because the user already framed the square in the crop dialog;
 * [save] only re-centres a non-square bitmap defensively.
 */
interface Avatars {
    /** @return the upright bitmap to crop, or null when the picked image cannot be read. */
    suspend fun decode(uri: String): Bitmap?

    /** @return a `file:` URI string for the new picture, or null when it cannot be written. */
    suspend fun save(bitmap: Bitmap, previous: String?): String?

    /** Removes a stored picture; ignores anything that is not one of ours. */
    suspend fun delete(avatar: String?)
}

class AvatarStore(context: Context) : Avatars {
    private val context = context.applicationContext
    private val avatars: File
        get() = File(context.filesDir, AVATAR_DIR)

    override suspend fun decode(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        // The picker grant can expire between choosing and reading; treat that as "no picture".
        runCatching { PickedPhoto.decode(context, uri.toUri(), maxEdge = SOURCE_EDGE) }.getOrNull()
    }

    override suspend fun save(bitmap: Bitmap, previous: String?): String? = withContext(Dispatchers.IO) {
        var square: Bitmap? = null
        var prepared: Bitmap? = null
        var destination: File? = null
        var complete = false
        try {
            val cropped = centerSquare(bitmap).also { square = it.takeIf { temp -> temp !== bitmap } }
            val target = minOf(SIZE, cropped.width).coerceAtLeast(1)
            val scaled = if (cropped.width == target) cropped else cropped.scale(target, target, true)
            prepared = scaled.takeIf { it !== bitmap && it !== square }
            val folder = avatars.apply { mkdirs() }
            val file = File(folder, "avatar-${UUID.randomUUID()}.jpg")
            destination = file
            FileOutputStream(file).use { output ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 90, output)) throw IOException("头像无法编码")
                output.fd.sync()
            }
            complete = true
            // The old file is only dropped once the new one is safely on disk; failing to clean
            // it up must not fail the save.
            runCatching { delete(previous) }
            Uri.fromFile(file).toString()
        } catch (_: IOException) {
            null
        } finally {
            prepared?.recycle()
            square?.recycle()
            if (!complete) destination?.delete()
        }
    }

    override suspend fun delete(avatar: String?) = withContext(Dispatchers.IO) {
        if (avatar == null || !avatar.startsWith("file:")) return@withContext
        val path = avatar.toUri().path ?: return@withContext
        val file = File(path).canonicalFile
        // Only ever delete inside our own directory, never an arbitrary path.
        if (file.parentFile == avatars.canonicalFile && file.name.matches(Regex("avatar-[a-f0-9-]+\\.jpg"))) {
            if (file.exists() && !file.delete()) throw IOException("头像无法移除")
        }
    }

    /** Center-crops to a square; returns [bitmap] itself when it already is one. */
    private fun centerSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        if (side == bitmap.width && side == bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
    }

    companion object {
        private const val AVATAR_DIR = "avatars"

        /** Big enough for a 96dp avatar on a 3x screen, small enough to stay a few dozen KB. */
        private const val SIZE = 512

        /** Headroom for the crop dialog: the user may pick a face out of a large picture. */
        private const val SOURCE_EDGE = 2048
    }
}
