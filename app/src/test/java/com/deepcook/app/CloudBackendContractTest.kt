package com.deepcook.app

import com.deepcook.app.core.model.*
import com.deepcook.app.data.cloud.*
import com.deepcook.app.data.json
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in end-to-end native DTO/API test against server/dev.py, strictly limited to loopback. */
class CloudBackendContractTest {
    @Test fun accountKitchenPicturesAndPublicCommunityWorkAcrossTwoNativeClients() = runBlocking {
        val base = System.getenv("DEEPCOOK_CONTRACT_API")
        assumeTrue("Run server/dev.py and set DEEPCOOK_CONTRACT_API for this local contract test", base != null)
        require(URI(base!!).host in setOf("127.0.0.1", "localhost")) { "Contract tests never write production data" }
        fun api() = CloudApi(object : CloudCredentialStore {
            private var value = CloudCredentials()
            override fun read() = value
            override fun write(value: CloudCredentials) { this.value = value }
        }, base, "http://127.0.0.1:4173")
        val first = api()
        val username = "android_" + UUID.randomUUID().toString().take(8)
        val login = buildJsonObject { put("username", username); put("password", "cook26"); put("claimGuest", false) }
        val result = first.request("/auth/register", "POST", login, expected = null)
        val account = json.decodeFromJsonElement<CloudAccount>(result.getValue("account"))
        assertTrue(account.createdAt > 0)
        first.setAccount(account)
        val folder = kotlin.io.path.createTempDirectory("deepcook-backend-contract-").toFile()
        try {
            val jpeg = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB), "jpeg", it) }.toByteArray()
            val picture = first.request("/account/media", "PUT", bytes = jpeg).getValue("photoUrl").jsonPrimitive.content
            val media = object : KitchenMedia { override suspend fun privatePhoto(path: String, expected: String) = picture }
            val kitchen = CloudKitchen(first, folder, media)
            kitchen.activate(account.id)
            val personalId = "user-${UUID.randomUUID()}"
            kitchen.mutate { current -> current.copy(
                user = current.user.withFavorite("braised-pork", true, System.currentTimeMillis()).copy(
                    profile = UserProfile("跨端厨房", picture, setOf("清淡"), setOf("香菜")),
                    preferences = UserPreferences(themeMode = ThemeMode.DARK, speakAnswers = false),
                    foodSetup = FoodSetup(profile = FoodProfile(cuisines = setOf("中式家常")), completed = true),
                    dishNotes = listOf(DishNote(UUID.randomUUID().toString(), "braised-pork", "少放盐"))),
                recipes = listOf(Recipe(id = personalId, title = "自己的汤", description = "测试私有菜谱", cover = picture,
                    categories = listOf("家常菜"), servings = "1 人份", ingredients = listOf(Ingredient("i1", "水", "1 杯")),
                    steps = listOf(RecipeStep("s1", 1, "煮开", "煮开即可", picture, minutes = 2, kind = StepKind.PASSIVE)))),
                covers = mapOf("braised-pork" to picture),
                privateCommunity = mapOf(personalId to PrivateCommunity(9, listOf(PrivatePost("post-${UUID.randomUUID()}", "只给自己看", true, picture, System.currentTimeMillis())))),
            ) }
            first.request("/recipes/braised-pork/rating", "PUT", buildJsonObject { put("score", 9) })
            val post = "post-${UUID.randomUUID()}"
            first.request("/recipes/braised-pork/uploads/$post", "PUT", bytes = jpeg)
            first.request("/recipes/braised-pork/posts", "POST", buildJsonObject {
                put("id", post); put("text", "跨端作业"); put("submission", true); put("photo", true)
            })
            val public = first.request("/recipes/braised-pork")
            assertEquals(9, public.getValue("ownRating").jsonPrimitive.int)
            assertTrue(public.getValue("posts").jsonArray.any { it.jsonObject["id"]?.jsonPrimitive?.content == post })
            val other = api()
            val anonymous = other.request("/recipes/braised-pork", expected = null)
            assertTrue(anonymous.getValue("posts").jsonArray.none { it.jsonObject.getValue("mine").jsonPrimitive.boolean })
            other.http.newCall(Request.Builder().url(other.mediaUrl(picture)).build()).execute().use { assertEquals(401, it.code) }
            other.request("/auth/login", "POST", login, expected = null)
            other.setAccount(account)
            val secondFolder = kotlin.io.path.createTempDirectory("deepcook-backend-second-").toFile()
            try {
                val second = CloudKitchen(other, secondFolder, media); second.activate(account.id)
                assertEquals("跨端厨房", second.state.value.user.profile.displayName)
                assertFalse(second.state.value.user.preferences.speakAnswers)
                assertEquals(StepKind.PASSIVE, second.state.value.recipes.single().steps.single().kind)
                assertEquals(picture, second.state.value.covers["braised-pork"])
                assertEquals(1, second.state.value.privateCommunity[personalId]!!.posts.size)
                other.http.newCall(Request.Builder().url(other.mediaUrl(picture)).header("X-DeepCook-Account", account.id).build())
                    .execute().use { assertEquals(200, it.code) }
                first.request("/auth/change-password", "POST", buildJsonObject { put("currentPassword", "cook26"); put("newPassword", "chef26") })
                val expired = other.request("/auth/session", expected = null)
                assertTrue(expired["account"] is JsonNull)
            } finally { secondFolder.deleteRecursively() }
        } finally { folder.deleteRecursively() }
    }
}
