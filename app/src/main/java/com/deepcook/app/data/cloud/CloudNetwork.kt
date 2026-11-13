package com.deepcook.app.data.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.deepcook.app.data.json
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

const val CLOUD_BASE = "https://deepcook-backend.jijiz.cn/deepcook-api/v1"
const val CLOUD_ORIGIN = "https://deepcook.jijiz.cn"

@Serializable
data class CloudAccount(val id: String, val username: String, val name: String, val createdAt: Long = 0)

@Serializable
data class CloudCredentials(
    val visitor: String = newVisitor(),
    val cookie: String? = null,
    val expiresAt: Long = 0,
    val account: CloudAccount? = null,
    val issuedAt: Long = 0,
)

private fun newVisitor(): String = ByteArray(32).also(SecureRandom()::nextBytes)
    .joinToString("") { "%02x".format(it.toInt() and 255) }

interface CloudCredentialStore {
    fun read(): CloudCredentials
    fun write(value: CloudCredentials)
}

/** Credentials never enter DataStore exports, APK resources, Android backup, or HTTP logs. */
class EncryptedCloudCredentialStore(context: Context) : CloudCredentialStore {
    private val file = File(context.noBackupFilesDir, "cloud-session.enc")
    private val alias = "deepcook.cloud.session.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized override fun read(): CloudCredentials {
        if (!file.exists()) return CloudCredentials()
        val bytes = file.readBytes()
        require(bytes.size >= 29) { "登录缓存无法读取" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return json.decodeFromString<CloudCredentials>(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString())
    }
    @Synchronized override fun write(value: CloudCredentials) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(json.encodeToString(value).encodeToByteArray())
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, file.name + ".pending")
        pending.outputStream().use { it.write(bytes); it.fd.sync() }
        if (!pending.renameTo(file)) throw IOException("登录缓存无法保存")
    }
}

class CloudAccountScope(val id: String?) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<CloudAccountScope>
}

class CloudException(val status: Int, override val message: String) : IOException(message)

class CloudApi(
    private val storage: CloudCredentialStore,
    val base: String = CLOUD_BASE,
    private val origin: String = CLOUD_ORIGIN,
) {
    private val url = base.toHttpUrl()
    private val lock = Any()
    private var value = runCatching { storage.read().also(storage::write) }.getOrElse {
        // Retain an unreadable encrypted file until a successful explicit sign-in replaces it.
        CloudCredentials()
    }
    val credentials: CloudCredentials get() = synchronized(lock) { value }
    val accountId: String? get() = credentials.account?.id
    var onExpired: (() -> Unit)? = null

    val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host != url.host || request.url.port != url.port || request.url.scheme != url.scheme ||
                !request.url.encodedPath.startsWith(url.encodedPath.trimEnd('/') + "/")) {
                chain.proceed(request)
            } else {
                val state = credentials
                val expected = request.header("X-DeepCook-Account")
                if (expected != null && state.account?.id != expected)
                    throw CloudException(401, "登录身份已变化，请重试。")
                chain.proceed(request.newBuilder()
                    .header("Origin", origin)
                    .header("Authorization", "Bearer ${state.visitor}")
                    .apply { if (state.cookie != null && state.expiresAt > System.currentTimeMillis()) header("Cookie", state.cookie) }
                    .build())
            }
        }.build()

    private fun update(transform: (CloudCredentials) -> CloudCredentials) = synchronized(lock) {
        val next = transform(value)
        storage.write(next)
        value = next
    }
    fun setAccount(account: CloudAccount) = update { it.copy(account = account, issuedAt = System.currentTimeMillis()) }
    fun clear() = update { it.copy(cookie = null, expiresAt = 0, account = null, issuedAt = 0) }
    fun mediaUrl(path: String): String = url.newBuilder().encodedPath(path).query(null).build().toString()

    suspend fun request(path: String, method: String = "GET", body: JsonObject? = null,
                        expected: String? = accountId, bytes: ByteArray? = null): JsonObject = withContext(Dispatchers.IO) {
        val scope = kotlinx.coroutines.currentCoroutineContext()[CloudAccountScope]
        if (scope != null && expected != null && scope.id != expected)
            throw CloudException(401, "登录身份已变化，请重新打开页面后重试。")
        val requestBody = when {
            bytes != null -> bytes.toRequestBody("image/jpeg".toMediaType())
            body != null -> body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            method in listOf("POST", "PUT") -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val request = Request.Builder().url(base.trimEnd('/') + path).method(method, requestBody)
            .header("Cache-Control", "no-store")
            .apply { if (expected != null) header("X-DeepCook-Account", expected) }.build()
        try {
            http.newCall(request).execute().use { response ->
                if (path.startsWith("/auth/")) {
                    response.headers.values("Set-Cookie").mapNotNull { Cookie.parse(request.url, it) }
                        .firstOrNull { it.name == "__Host-deepcook_session" || (url.scheme == "http" && it.name == "deepcook_session") }
                        ?.let { cookie -> update { it.copy(cookie = "${cookie.name}=${cookie.value}", expiresAt = cookie.expiresAt) } }
                }
                val payload = runCatching { json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject }
                    .getOrElse { throw CloudException(response.code, "云端返回异常，请稍后重试。") }
                if (!response.isSuccessful) {
                    if (response.code == 401 && expected != null && accountId == expected) {
                        clear(); onExpired?.invoke()
                    }
                    throw CloudException(response.code, payload["error"]?.jsonPrimitive?.content ?: "云端请求未完成。")
                }
                payload
            }
        } catch (error: CloudException) { throw error }
        catch (error: IOException) { throw CloudException(0, "暂时无法连接云端，原数据和草稿已保留。") }
    }
}
