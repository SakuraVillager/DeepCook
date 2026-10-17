package com.deepcook.app.data.lookup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The smallest thing the lookup needs from the network, so tests can run without one. */
interface HttpText {
    /** @return the response body; throws [IOException] on a non-2xx status or a transport error. */
    suspend fun get(url: String): String
}

/** Reports whether a lookup has any chance of succeeding right now. */
fun interface NetworkStatus {
    fun isOnline(): Boolean
}

class UrlConnectionHttpText(private val userAgent: String = "DeepCook-Android") : HttpText {
    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "text/plain, text/markdown, */*")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("HTTP $status")
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 1_048_576) throw IOException("菜谱资料过大")
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally {
            connection.disconnect()
        }
    }
}

class AndroidNetworkStatus(context: Context) : NetworkStatus {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val active = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * Prefills a user recipe from the bundled copy of the HowToCook catalog.
 *
 * The app ships the *name index* (`assets/howtocook_index.json`) but not the pages: the index is
 * a few tens of kilobytes, keeps name matching working offline, and pins the commit the content
 * was reviewed at. A lookup therefore costs exactly one request, and that request is the only
 * part that needs a network.
 */
class HowToCookLookup(
    private val context: Context,
    private val http: HttpText = UrlConnectionHttpText(),
    private val network: NetworkStatus = AndroidNetworkStatus(context),
) : RecipeLookup {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedIndex: CookIndex? = null

    override suspend fun lookup(name: String): LookupOutcome {
        val query = name.trim()
        if (query.isEmpty()) return LookupOutcome.NotFound

        // Match first: a known dish name with no network is still a useful answer to report.
        val catalog = index()
        val entry = DishMatcher.match(query, catalog.dishes) ?: return LookupOutcome.NotFound
        if (!network.isOnline()) return LookupOutcome.Offline

        val url = pageUrl(catalog, entry)
        return try {
            val markdown = http.get(url)
            val draft = HowToCookParser.parse(markdown, entry.group, url)
                ?: return LookupOutcome.Failed("这道菜的资料没能读懂，请手动填写。")
            LookupOutcome.Filled(
                draft = draft,
                matchedName = entry.name,
                sourceUrl = url,
                sourceName = "HowToCook",
            )
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            LookupOutcome.Failed("联网获取失败，可以检查网络后重试，或直接手动填写。")
        }
    }

    private suspend fun index(): CookIndex {
        cachedIndex?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            context.assets.open(INDEX_ASSET).bufferedReader(Charsets.UTF_8).use {
                json.decodeFromString<CookIndex>(it.readText())
            }
        }
        cachedIndex = loaded
        return loaded
    }

    private fun pageUrl(catalog: CookIndex, entry: DishEntry): String {
        val path = entry.path.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        return "https://raw.githubusercontent.com/${catalog.source}/${catalog.ref}/$path"
    }

    companion object {
        const val INDEX_ASSET = "howtocook_index.json"
    }
}
