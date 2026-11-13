package com.deepcook.app

import com.deepcook.app.core.model.*
import com.deepcook.app.data.cloud.*
import com.deepcook.app.data.json
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

private class MemoryCredentials : CloudCredentialStore {
    private var value = CloudCredentials()
    override fun read() = value
    override fun write(value: CloudCredentials) { this.value = value }
}

/** Real HTTP transport, deterministic local server, no Android device or production writes. */
private class CloudFixture : AutoCloseable {
    val account = CloudAccount("00000000-0000-4000-8000-000000000001", "kitchen", "厨房", 1700000000000)
    var value = NativeKitchen().wire()
    var revision = 0L
    val operations = mutableSetOf<String>()
    var conflictNext = false
    var commitWithUnavailableResponse = false
    var lastCookie: String? = null
    var lastOrigin: String? = null
    var lastAccount: String? = null
    var reads = 0
    val requests = mutableListOf<String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        this.executor = this@CloudFixture.executor
        createContext("/deepcook-api/v1/") { exchange ->
            val path = exchange.requestURI.path.removePrefix("/deepcook-api/v1")
            requests.add(path)
            lastCookie = exchange.requestHeaders.getFirst("Cookie")
            lastOrigin = exchange.requestHeaders.getFirst("Origin")
            lastAccount = exchange.requestHeaders.getFirst("X-DeepCook-Account")
            var status = 200
            val response = when (path) {
                "/auth/register", "/auth/login" -> {
                    exchange.responseHeaders.add("Set-Cookie", "deepcook_session=${"a".repeat(64)}; Path=/; HttpOnly; Max-Age=604800")
                    buildJsonObject { put("account", json.encodeToJsonElement(account)); put("emailVerification", false) }
                }
                "/auth/session" -> buildJsonObject { put("account", json.encodeToJsonElement(account)); put("emailVerification", false) }
                "/account/data" -> {
                    if (exchange.requestMethod == "GET") { reads++; document() }
                    else {
                        val body = json.parseToJsonElement(exchange.requestBody.readBytes().decodeToString()).jsonObject
                        val mutation = body.getValue("mutationId").jsonPrimitive.content
                        when {
                            mutation in operations -> document()
                            conflictNext -> {
                                conflictNext = false; revision++
                                value = value.copy(preferences = value.preferences.copy(theme = "dark"))
                                status = 409; buildJsonObject { put("error", "另一台设备已更新") }
                            }
                            body.getValue("revision").jsonPrimitive.long != revision -> {
                                status = 409; buildJsonObject { put("error", "版本冲突") }
                            }
                            else -> {
                                value = json.decodeFromJsonElement(body.getValue("data")); revision++; operations.add(mutation)
                                if (commitWithUnavailableResponse) {
                                    commitWithUnavailableResponse = false; status = 503
                                    buildJsonObject { put("error", "response unavailable after commit") }
                                } else document()
                            }
                        }
                    }
                }
                else -> { status = 404; buildJsonObject { put("error", "missing") } }
            }
            val bytes = response.toString().encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        start()
    }
    val api = CloudApi(MemoryCredentials(), "http://127.0.0.1:${server.address.port}/deepcook-api/v1")
    val folder = kotlin.io.path.createTempDirectory("deepcook-cloud-unit-").toFile()
    val media = object : KitchenMedia {
        override suspend fun privatePhoto(path: String, expected: String) = "/deepcook-api/v1/account/media/${"b".repeat(64)}.jpg"
    }
    fun kitchen() = CloudKitchen(api, folder, media)
    private fun document() = buildJsonObject {
        put("revision", revision); put("data", json.encodeToJsonElement(value)); put("account", json.encodeToJsonElement(account))
    }
    override fun close() { server.stop(0); executor.shutdownNow(); folder.deleteRecursively() }
}

class CloudIntegrationTest {
    @Test fun cookiesAndExpectedAccountReachOnlyTheBackend() = runBlocking {
        CloudFixture().use { server ->
            server.api.request("/auth/login", "POST", buildJsonObject {
                put("username", "kitchen"); put("password", "cook26"); put("claimGuest", false)
            }, expected = null)
            server.api.request("/auth/session", expected = null)
            assertTrue(server.lastCookie!!.startsWith("deepcook_session="))
            assertNull(server.lastAccount)
            assertEquals(CLOUD_ORIGIN, server.lastOrigin)
            server.api.setAccount(server.account)
            server.api.request("/account/data")
            assertEquals(server.account.id, server.lastAccount)
            assertFalse(server.api.credentials.visitor.isBlank())
        }
    }
    @Test fun aConcurrentWebEditIsPreservedWhenTheAppRebasesItsFavorite() = runBlocking {
        CloudFixture().use { server ->
            server.api.setAccount(server.account)
            val kitchen = server.kitchen(); kitchen.activate(server.account.id)
            server.conflictNext = true
            kitchen.mutate { it.copy(user = it.user.withFavorite("braised-pork", true, 1700000000000)) }
            assertEquals("dark", server.value.preferences.theme)
            assertEquals(listOf("braised-pork"), server.value.favorites.map { it.recipeId })
            assertEquals(2L, server.revision)
        }
    }
    @Test fun aLostSaveSurvivesRepositoryRestartAndReusesItsOperationId() = runBlocking {
        CloudFixture().use { server ->
            server.api.setAccount(server.account)
            val first = server.kitchen(); first.activate(server.account.id)
            server.commitWithUnavailableResponse = true
            try {
                first.mutate { it.copy(user = it.user.withFavorite("braised-pork", true, 1)) }
                fail("a lost response must remain unconfirmed")
            } catch (error: CloudException) { assertEquals(503, error.status) }
            assertTrue(File(server.folder, "${server.account.id}.outbox.json").exists())
            val restored = server.kitchen(); restored.activate(server.account.id)
            restored.mutate { it.copy(user = it.user.withFavorite("world-ukrainian-borscht", true, 2)) }
            assertEquals(2L, server.revision)
            assertEquals(2, server.operations.size)
            assertEquals(2, server.value.favorites.size)
            assertFalse(File(server.folder, "${server.account.id}.outbox.json").exists())
        }
    }
    @Test fun aQueuedOldAccountEditCannotWriteAsTheNextUser() = runBlocking {
        CloudFixture().use { server ->
            server.api.setAccount(server.account)
            val kitchen = server.kitchen(); kitchen.activate(server.account.id)
            val before = server.requests.size
            server.api.setAccount(server.account.copy(id = "00000000-0000-4000-8000-000000000002"))
            try {
                kitchen.mutate { it.copy(user = it.user.copy(profile = UserProfile(displayName = "不得串号"))) }
                fail("a stale account must be rejected")
            } catch (error: CloudException) { assertEquals(401, error.status) }
            assertEquals(before, server.requests.size)
            assertEquals(0L, server.revision)
        }
    }
    @Test fun anOldUiScopeCannotSubmitIntoAnAlreadyActivatedNewAccount() = runBlocking {
        CloudFixture().use { server ->
            server.api.setAccount(server.account)
            val kitchen = server.kitchen(); kitchen.activate(server.account.id)
            val before = server.requests.size
            try {
                kotlinx.coroutines.withContext(CloudAccountScope("old-account")) {
                    kitchen.mutate { it.copy(user = it.user.copy(profile = UserProfile(displayName = "旧页面"))) }
                }
                fail("the UI scope must match the active account")
            } catch (error: CloudException) { assertEquals(401, error.status) }
            assertEquals(before, server.requests.size)
        }
    }
    @Test fun webPreferencesOverrideSharedAndroidSettingsButRetainAppOnlyFields() {
        val wire = WireKitchen(preferences = WirePreferences(theme = "dark", readingSize = "large"), android = AndroidKitchen(
            preferences = UserPreferences(colorSource = ColorSource.DYNAMIC, speakAnswers = false),
            foodSetup = FoodSetup(profile = FoodProfile(cuisines = setOf("中式家常")), completed = true),
            dishNotes = listOf(DishNote("note", "braised-pork", "少放盐"))))
        val native = wire.native()
        assertEquals(ThemeMode.DARK, native.user.preferences.themeMode)
        assertEquals(ColorSource.DYNAMIC, native.user.preferences.colorSource)
        assertFalse(native.user.preferences.speakAnswers)
        assertEquals(setOf("中式家常"), native.user.foodSetup.profile.cuisines)
        val roundtrip = json.decodeFromString<WireKitchen>(json.encodeToString(native.wire())).native()
        assertEquals(native, roundtrip)
    }
    @Test fun publicRatingCountsDoNotExpandIntoAPotentiallyHugeList() {
        val value = WireCommunity("braised-pork", ownRating = 9, ratingCounts = List(10) { if (it == 8) 1_000_000 else 0 },
            posts = listOf(WirePost("post", "braised-pork", false, "别人的评论", author = "邻居", mine = false, createdAt = 1)),
            postCounts = WireCounts(comment = 1), cursors = WireCursors()).native(null)
        assertEquals(1_000_000L, value.ratingCount)
        assertEquals(9.0, value.average!!, 0.0)
        assertTrue(value.ratings.isEmpty())
        assertFalse(value.posts.single().mine)
    }
}
