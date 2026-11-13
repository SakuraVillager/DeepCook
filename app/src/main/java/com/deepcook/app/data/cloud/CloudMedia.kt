package com.deepcook.app.data.cloud

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.deepcook.app.DeepCookApplication
import com.deepcook.app.data.PickedPhoto
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive

class CloudMedia(private val context: Context, private val api: CloudApi) : KitchenMedia {
    override suspend fun privatePhoto(path: String, expected: String): String {
        val result = api.request("/account/media", "PUT", expected = expected, bytes = jpeg(path, false))
        return result.getValue("photoUrl").jsonPrimitive.content.also {
            require(it.matches(Regex("/deepcook-api/v1/account/media/[a-f0-9]{64}\\.jpg"))) { "云端图片地址无效" }
        }
    }
    suspend fun jpeg(path: String, public: Boolean): ByteArray = withContext(Dispatchers.IO) {
        val original = PickedPhoto.decode(context, Uri.parse(path), maxEdge = 2400) ?: throw IOException("照片无法读取，请重新选择。")
        var cropped = original
        var resized = original
        try {
            if (public) {
                val width = minOf(original.width, original.height * 4 / 3)
                val height = minOf(original.height, original.width * 3 / 4)
                cropped = Bitmap.createBitmap(original, (original.width - width) / 2, (original.height - height) / 2, width, height)
                resized = Bitmap.createScaledBitmap(cropped, 1200, 900, true)
            } else {
                val factor = minOf(1.0, 1200.0 / maxOf(original.width, original.height))
                resized = Bitmap.createScaledBitmap(original, (original.width * factor).toInt().coerceAtLeast(1), (original.height * factor).toInt().coerceAtLeast(1), true)
            }
            for (quality in listOf(82, 65, 50)) {
                val output = ByteArrayOutputStream()
                if (!resized.compress(Bitmap.CompressFormat.JPEG, quality, output)) throw IOException("照片无法编码。")
                val bytes = output.toByteArray()
                if (bytes.size <= 1024 * 1024) return@withContext bytes
            }
            throw IOException("照片压缩后仍超过 1 MB，请换一张试试。")
        } finally {
            if (resized !== cropped && resized !== original) resized.recycle()
            if (cropped !== original) cropped.recycle()
            original.recycle()
        }
    }
}

/** Private images share the API's cookie transport, but never its public image cache. */
fun cloudImageModel(context: Context, path: String?): Any? {
    if (path == null) return null
    val api = (context.applicationContext as? DeepCookApplication)?.container?.cloudApi
    if (path.startsWith("/deepcook-api/v1/")) {
        if (api == null) return null
        val url = api.mediaUrl(path)
        if (path.startsWith("/deepcook-api/v1/account/media/")) {
            val account = api.accountId ?: return null
            return ImageRequest.Builder(context).data(url)
                .httpHeaders(NetworkHeaders.Builder().set("X-DeepCook-Account", account).build())
                .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
        }
        return url
    }
    return when {
        path.startsWith("file:") || path.startsWith("content:") || path.startsWith("https://") -> path
        else -> "file:///android_asset/$path"
    }
}
