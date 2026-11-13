package com.deepcook.app.data.cloud

import com.deepcook.app.core.model.*
import com.deepcook.app.data.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class WireProfile(val name: String = "", val avatar: String? = null,
                                    val likes: Set<String> = emptySet(), val avoids: Set<String> = emptySet())
@Serializable data class WirePreferences(val theme: String = "system", val readingSize: String = "standard",
                                        val showStepImages: Boolean = true, val keepScreenOn: Boolean = false)
@Serializable data class PrivatePost(val id: String, val text: String, val submission: Boolean, val photo: String? = null, val createdAt: Long)
@Serializable data class PrivateCommunity(val score: Int? = null, val posts: List<PrivatePost> = emptyList())
@Serializable data class AndroidKitchen(val schema: Int = 1, val preferences: UserPreferences = UserPreferences(),
    val foodSetup: FoodSetup = FoodSetup(), val dishNotes: List<DishNote> = emptyList(),
    val coverOverrides: Map<String, String> = emptyMap(), val privateCommunity: Map<String, PrivateCommunity> = emptyMap())
@Serializable data class WireKitchen(val favorites: List<SavedRecipe> = emptyList(), val recent: List<SavedRecipe> = emptyList(),
    val recipes: List<Recipe> = emptyList(), val profile: WireProfile = WireProfile(),
    val preferences: WirePreferences = WirePreferences(), val android: AndroidKitchen? = null)
@Serializable data class KitchenSnapshot(val revision: Long, val data: WireKitchen)
data class NativeKitchen(val user: UserData = UserData(), val recipes: List<Recipe> = emptyList(),
                         val covers: Map<String, String> = emptyMap(), val privateCommunity: Map<String, PrivateCommunity> = emptyMap())

fun WireKitchen.native(): NativeKitchen {
    val extras = android ?: AndroidKitchen()
    val preferences = extras.preferences.copy(
        themeMode = when (preferences.theme) { "dark" -> ThemeMode.DARK; "light" -> ThemeMode.LIGHT; else -> ThemeMode.SYSTEM },
        readingSize = when (preferences.readingSize) { "large" -> ReadingSize.LARGE; "extra" -> ReadingSize.EXTRA_LARGE; else -> ReadingSize.STANDARD },
        showStepImages = preferences.showStepImages, keepScreenOn = preferences.keepScreenOn,
    )
    return NativeKitchen(UserData(favorites, recent, preferences, extras.foodSetup,
        UserProfile(profile.name, profile.avatar, profile.likes, profile.avoids), extras.dishNotes), recipes, extras.coverOverrides, extras.privateCommunity)
}
fun NativeKitchen.wire(): WireKitchen = WireKitchen(user.favorites, user.recent.take(50), recipes,
    WireProfile(user.profile.displayName, user.profile.avatar, user.profile.likes, user.profile.avoids),
    WirePreferences(user.preferences.themeMode.name.lowercase(),
        when (user.preferences.readingSize) { ReadingSize.LARGE -> "large"; ReadingSize.EXTRA_LARGE -> "extra"; else -> "standard" },
        user.preferences.showStepImages, user.preferences.keepScreenOn),
    AndroidKitchen(preferences = user.preferences, foodSetup = user.foodSetup, dishNotes = user.dishNotes, coverOverrides = covers, privateCommunity = privateCommunity))

interface KitchenMedia {
    suspend fun privatePhoto(path: String, expected: String): String
}

/** One queue per account: every write rebases a semantic edit on the latest complete document. */
class CloudKitchen(
    val api: CloudApi,
    private val folder: File,
    private val media: KitchenMedia,
) {
    val active = MutableStateFlow<String?>(null)
    val state = MutableStateFlow(NativeKitchen())
    val syncError = MutableStateFlow<String?>(null)
    private val mutex = Mutex()
    private val pending = mutableMapOf<String, Pair<String, JsonObject>>()
    private fun cache(id: String) = File(folder, "$id.json")
    private fun outbox(id: String) = File(folder, "$id.outbox.json")
    private suspend fun stage(id: String, body: JsonObject) = withContext(Dispatchers.IO) {
        folder.mkdirs()
        val file = outbox(id)
        val temporary = File(folder, "$id.outbox.pending")
        temporary.outputStream().use { it.write(body.toString().encodeToByteArray()); it.fd.sync() }
        check(temporary.renameTo(file)) { "待确认的保存无法记录，尚未发送云端。" }
        pending[id] = body.getValue("mutationId").jsonPrimitive.content to body
    }
    private suspend fun clearPending(id: String) = withContext(Dispatchers.IO) {
        pending.remove(id)
        check(!outbox(id).exists() || outbox(id).delete()) { "保存已确认，但重试记录无法更新。" }
    }
    private suspend fun snapshot(id: String): KitchenSnapshot {
        val response = api.request("/account/data", expected = id)
        if (response["account"]?.jsonObject?.get("id")?.jsonPrimitive?.content != id)
            throw CloudException(401, "云端账号与当前厨房不一致。")
        return KitchenSnapshot(response.getValue("revision").jsonPrimitive.long,
            json.decodeFromJsonElement(response.getValue("data")))
    }
    private suspend fun accept(id: String, value: KitchenSnapshot) = withContext(Dispatchers.IO) {
        if (api.accountId != id) throw CloudException(401, "登录身份已变化，请重试。")
        folder.mkdirs()
        val staged = File(folder, "$id.pending")
        staged.outputStream().use { it.write(json.encodeToString(value).encodeToByteArray()); it.fd.sync() }
        if (!staged.renameTo(cache(id))) throw IOException("云端资料已保存，但本机缓存无法更新。")
        if (active.value == id) state.value = value.data.native()
    }
    suspend fun activate(id: String) = mutex.withLock {
        val cached = withContext(Dispatchers.IO) { runCatching { json.decodeFromString<KitchenSnapshot>(cache(id).readText()) }.getOrNull() }
        val latest = try { snapshot(id).also { accept(id, it) } }
        catch (error: CloudException) {
            if (error.status != 0 && error.status != 429 && error.status < 500 || cached == null) throw error
            syncError.value = error.message
            cached
        }
        active.value = id
        state.value = latest.data.native()
    }
    suspend fun deactivate() = mutex.withLock {
        active.value = null
        state.value = NativeKitchen()
        syncError.value = null
    }
    suspend fun refresh() = mutex.withLock {
        val id = active.value ?: return@withLock
        try { accept(id, snapshot(id)); syncError.value = null }
        catch (error: CloudException) { syncError.value = error.message; throw error }
    }
    private suspend fun resolvePending(id: String) {
        val operation = pending[id] ?: withContext(Dispatchers.IO) {
            if (!outbox(id).exists()) null else json.parseToJsonElement(outbox(id).readText()).jsonObject.let {
                it.getValue("mutationId").jsonPrimitive.content to it
            }
        } ?: return
        val result = try { api.request("/account/data", "PUT", operation.second, id) }
        catch (error: CloudException) {
            if (error.status == 409) {
                withContext(Dispatchers.IO) {
                    val previous = File(folder, "$id.conflict-${System.currentTimeMillis()}.json")
                    if (outbox(id).exists()) check(outbox(id).renameTo(previous))
                    pending.remove(id)
                }
                throw CloudException(409, "上次保存与另一台设备的修改冲突，请刷新核对后重新保存；本机原件已保留。")
            }
            if (error.status in 400..499 && error.status != 409) clearPending(id)
            throw error
        }
        accept(id, KitchenSnapshot(result.getValue("revision").jsonPrimitive.long,
            json.decodeFromJsonElement(result.getValue("data"))))
        clearPending(id)
    }
    suspend fun mutate(transform: (NativeKitchen) -> NativeKitchen) {
        val id = active.value ?: throw CloudException(401, "请先登录厨房账号。")
        val scope = kotlinx.coroutines.currentCoroutineContext()[CloudAccountScope]
        if (scope != null && scope.id != id) throw CloudException(401, "登录身份已变化，请重试。")
        mutex.withLock {
        if (active.value != id || api.accountId != id) throw CloudException(401, "登录身份已变化，请重试。")
        // A lost response is retried before any later edit can be applied or replace its ID.
        resolvePending(id)
        val mutation = UUID.randomUUID().toString()
        for (attempt in 0..3) {
            val current = snapshot(id)
            var next = transform(current.data.native())
            val uploaded = mutableMapOf<String, String>()
            suspend fun photo(path: String?): String? {
                if (path == null || !path.startsWith("file:") && !path.startsWith("content:")) return path
                return uploaded[path] ?: media.privatePhoto(path, id).also { uploaded[path] = it }
            }
            next = next.copy(
                user = next.user.copy(profile = next.user.profile.copy(avatar = photo(next.user.profile.avatar))),
                recipes = next.recipes.map { recipe -> recipe.copy(cover = photo(recipe.cover),
                    steps = recipe.steps.map { step -> step.copy(image = photo(step.image)) }) },
                covers = next.covers.mapValues { (_, path) -> photo(path)!! },
            )
            val body = buildJsonObject {
                put("revision", current.revision); put("mutationId", mutation)
                put("data", json.encodeToJsonElement(next.wire()))
            }
            stage(id, body)
            try {
                var response: JsonObject
                try { response = api.request("/account/data", "PUT", body, id) }
                catch (error: CloudException) {
                    if (error.status != 0) throw error
                    response = api.request("/account/data", "PUT", body, id)
                }
                val saved = KitchenSnapshot(response.getValue("revision").jsonPrimitive.long,
                    json.decodeFromJsonElement(response.getValue("data")))
                accept(id, saved)
                clearPending(id)
                syncError.value = null
                return@withLock
            } catch (error: CloudException) {
                if (error.status != 409 || attempt == 3) {
                    if (error.status in 400..499) clearPending(id)
                    syncError.value = error.message; throw error
                }
                clearPending(id)
            }
        }
    }
    }
    suspend fun importGuest(user: UserData, recipes: List<Recipe>, covers: Map<String, String>) = mutate { current ->
        fun saved(remote: List<SavedRecipe>, local: List<SavedRecipe>) = (remote + local)
            .groupBy { it.recipeId }.map { (_, rows) -> rows.maxBy { it.timestamp } }.sortedByDescending { it.timestamp }
        val fresh = current.user.favorites.isEmpty() && current.user.recent.isEmpty() && current.recipes.isEmpty() && current.user.profile.likes.isEmpty()
        val avoids = current.user.profile.avoids + (user.profile.avoids - current.user.profile.likes)
        current.copy(user = current.user.copy(
            favorites = saved(current.user.favorites, user.favorites), recent = saved(current.user.recent, user.recent).take(50),
            profile = current.user.profile.copy(
                displayName = current.user.profile.displayName.ifBlank { user.profile.displayName },
                avatar = current.user.profile.avatar ?: user.profile.avatar,
                likes = (current.user.profile.likes + user.profile.likes) - avoids, avoids = avoids,
            ),
            preferences = if (fresh) user.preferences else current.user.preferences,
            foodSetup = if (current.user.foodSetup.completed) current.user.foodSetup else user.foodSetup,
            dishNotes = (user.dishNotes + current.user.dishNotes).associateBy { it.id }.values.toList().takeLast(UserData.NOTE_LIMIT),
        ), recipes = (recipes + current.recipes).associateBy { it.id }.values.toList(), covers = covers + current.covers)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CloudUserDataRepository(private val cloud: CloudKitchen, private val guest: UserDataRepository) : UserDataRepository {
    override val data: Flow<UserData> = cloud.active.flatMapLatest { if (it == null) guest.data else cloud.state.map { state -> state.user } }
    private suspend fun change(local: suspend () -> Unit, transform: (UserData) -> UserData) {
        if (cloud.active.value == null) throw CloudException(401, "请先登录厨房账号。")
        cloud.mutate { it.copy(user = transform(it.user)) }
    }
    override suspend fun setFavorite(id: String, favorite: Boolean, timestamp: Long) = change({ guest.setFavorite(id, favorite, timestamp) }) { it.withFavorite(id, favorite, timestamp) }
    override suspend fun recordVisit(id: String) = change({ guest.recordVisit(id) }) { it.withVisit(id, System.currentTimeMillis()) }
    override suspend fun removeVisit(id: String) = change({ guest.removeVisit(id) }) { it.copy(recent = it.recent.filterNot { row -> row.recipeId == id }) }
    override suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) = change({ guest.updatePreferences(transform) }) { it.copy(preferences = transform(it.preferences)) }
    override suspend fun updateProfile(transform: (UserProfile) -> UserProfile) = change({ guest.updateProfile(transform) }) { it.copy(profile = transform(it.profile).sanitized()) }
    override suspend fun addDishNote(note: DishNote) = change({ guest.addDishNote(note) }) { it.withNote(note) }
    override suspend fun updateDishNote(id: String, text: String, now: Long) = change({ guest.updateDishNote(id, text, now) }) { it.withEditedNote(id, text, now) }
    override suspend fun removeDishNote(id: String) = change({ guest.removeDishNote(id) }) { it.withoutNote(id) }
    override suspend fun clearRecent() = change({ guest.clearRecent() }) { it.copy(recent = emptyList()) }
    override suspend fun clearFavorites() = change({ guest.clearFavorites() }) { it.copy(favorites = emptyList()) }
    override suspend fun resetPreferences() = change({ guest.resetPreferences() }) { it.copy(preferences = UserPreferences()) }
    override suspend fun updateFoodSetup(transform: (FoodSetup) -> FoodSetup) = change({ guest.updateFoodSetup(transform) }) { it.copy(foodSetup = transform(it.foodSetup)) }
    override suspend fun forget(id: String) = change({ guest.forget(id) }) { it.copy(favorites = it.favorites.filterNot { row -> row.recipeId == id }, recent = it.recent.filterNot { row -> row.recipeId == id }, dishNotes = it.dishNotes.filterNot { note -> note.recipeId == id }) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CloudUserRecipeStore(private val cloud: CloudKitchen, private val guest: UserRecipeStore) : UserRecipeStore {
    override val recipes = cloud.active.flatMapLatest { if (it == null) guest.recipes else cloud.state.map { state -> state.recipes } }
    override suspend fun save(recipe: Recipe) {
        if (cloud.active.value == null) throw CloudException(401, "请先登录厨房账号。")
        cloud.mutate {
            it.copy(recipes = UserRecipeBook(it.recipes).withRecipe(recipe).validate().recipes)
        }
    }
    override suspend fun delete(id: String) {
        if (cloud.active.value == null) throw CloudException(401, "请先登录厨房账号。")
        cloud.mutate { it.copy(recipes = it.recipes.filterNot { recipe -> recipe.id == id }, privateCommunity = it.privateCommunity - id) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CloudCoverOverrides(private val cloud: CloudKitchen, private val guest: CoverOverrides) : CoverOverrides {
    override val entries = cloud.active.flatMapLatest { if (it == null) guest.entries else cloud.state.map { state -> state.covers } }
    override suspend fun put(recipeId: String, cover: String) {
        if (cloud.active.value == null) throw CloudException(401, "请先登录厨房账号。")
        cloud.mutate { it.copy(covers = it.covers + (recipeId to cover)) }
    }
    override suspend fun remove(recipeId: String) {
        if (cloud.active.value == null) throw CloudException(401, "请先登录厨房账号。")
        cloud.mutate { it.copy(covers = it.covers - recipeId) }
    }
}
