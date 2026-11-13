package com.deepcook.app.data.cloud

import com.deepcook.app.core.model.*
import com.deepcook.app.data.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class WirePost(val id: String, val recipeId: String, val submission: Boolean, val text: String,
    val photoUrl: String? = null, val author: String, val mine: Boolean, val createdAt: Long) {
    fun native() = KitchenPost(id, recipeId, submission, text, photoUrl, author, false, createdAt, mine)
}
@Serializable data class WireCounts(val comment: Int = 0, val submission: Int = 0)
@Serializable data class WireCursors(val comment: String? = null, val submission: String? = null)
@Serializable data class WireCommunity(val recipeId: String, val ownRating: Int? = null, val ratingCounts: List<Int>,
    val posts: List<WirePost>, val postCounts: WireCounts, val cursors: WireCursors) {
    fun native(nutrition: Nutrition?) = RecipeCommunity(nutrition = nutrition, ownRating = ownRating,
        posts = posts.map { it.native() }, ratingCounts = ratingCounts,
        commentCount = postCounts.comment, submissionCount = postCounts.submission,
        commentCursor = cursors.comment, submissionCursor = cursors.submission, cloud = true)
}
@Serializable private data class WirePage(val posts: List<WirePost>, val next: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class CloudCommunity(
    private val api: CloudApi,
    private val kitchen: CloudKitchen,
    private val local: KitchenStore,
    private val media: CloudMedia,
    private val folder: File,
) : KitchenCommunity {
    override val cloud = true
    override val accountId: String? get() = api.accountId
    override val favoriteExamples = flowOf(emptyMap<String, Int>())
    private val changed = MutableStateFlow(0L)
    private val refresh = MutableStateFlow(0L)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, WireCommunity>()
    private val mutex = Mutex()
    private fun identity() = api.accountId ?: "guest-${api.credentials.visitor.take(16)}"
    private fun key(recipeId: String) = "${identity()}_$recipeId"
    private fun route(recipeId: String) = "/recipes/$recipeId".also {
        require(recipeId.matches(Regex("[a-z0-9-]{1,128}"))) { "菜谱标识无效" }
    }
    private fun expected() = api.accountId ?: throw CloudException(401, "请先登录厨房账号。")
    override fun invalidate() { refresh.value++ }
    private suspend fun save(key: String, value: WireCommunity) = withContext(Dispatchers.IO) {
        folder.mkdirs()
        val pending = File(folder, "$key.pending")
        pending.outputStream().use { it.write(json.encodeToString(value).encodeToByteArray()); it.fd.sync() }
        check(pending.renameTo(File(folder, "$key.json"))) { "交流缓存无法保存" }
        cache[key] = value
        changed.value++
    }
    private suspend fun read(recipeId: String, id: String, key: String) {
        val value = json.decodeFromJsonElement<WireCommunity>(api.request(route(recipeId), expected = id))
        require(value.recipeId == recipeId && value.ratingCounts.size == 10 && value.ratingCounts.all { it >= 0 }) { "云端交流数据格式异常" }
        if (api.accountId != id) throw CloudException(401, "登录身份已变化，请重试。")
        save(key, value)
    }
    override fun observe(recipeId: String): Flow<RecipeCommunity> {
        if (recipeId.startsWith(USER_RECIPE_ID_PREFIX)) return kitchen.state.map { state ->
            val content = state.privateCommunity[recipeId] ?: PrivateCommunity()
            RecipeCommunity(ownRating = content.score, ratings = content.score?.let(::listOf) ?: emptyList(),
                posts = content.posts.sortedByDescending { it.createdAt }.map {
                    KitchenPost(it.id, recipeId, it.submission, it.text, it.photo, "我", false, it.createdAt, true)
                }, cloud = true)
        }
        return combine(kitchen.active, refresh) { id, _ -> id }.flatMapLatest { id -> flow {
            if (id == null) { emit(RecipeCommunity()); return@flow }
            val key = "${id}_$recipeId"
            val nutrition = local.observe(recipeId).first().nutrition
            withContext(Dispatchers.IO) {
                if (!cache.containsKey(key)) runCatching { json.decodeFromString<WireCommunity>(File(folder, "$key.json").readText()) }
                    .getOrNull()?.let { cache[key] = it }
            }
            cache[key]?.let { emit(it.native(nutrition)) }
            read(recipeId, id, key)
            emitAll(changed.map { cache.getValue(key).native(nutrition) })
        } }
    }
    override suspend fun rate(recipeId: String, score: Int) = mutex.withLock {
        require(score in 1..10)
        if (recipeId.startsWith(USER_RECIPE_ID_PREFIX)) {
            kitchen.mutate { state -> state.copy(privateCommunity = state.privateCommunity +
                (recipeId to (state.privateCommunity[recipeId] ?: PrivateCommunity()).copy(score = score))) }
        } else {
            val id = expected()
            api.request(route(recipeId) + "/rating", "PUT", buildJsonObject { put("score", score) }, id)
            read(recipeId, id, "${id}_$recipeId")
        }
    }
    override suspend fun post(recipeId: String, submission: Boolean, text: String, photo: String?, draftId: String?): String = mutex.withLock {
        require(text.isNotBlank() && text.length <= 2000) { "内容不能为空，最多 2000 字。" }
        require(submission || photo == null) { "评论不支持附图。" }
        val postId = draftId ?: "post-${UUID.randomUUID()}"
        val id = expected()
        if (recipeId.startsWith(USER_RECIPE_ID_PREFIX)) {
            val uploaded = photo?.let { media.privatePhoto(it, id) }
            val post = PrivatePost(postId, text.trim(), submission, uploaded, System.currentTimeMillis())
            kitchen.mutate { state ->
                val current = state.privateCommunity[recipeId] ?: PrivateCommunity()
                val previous = current.posts.find { it.id == postId }
                require(previous == null || previous.text == post.text && previous.submission == submission && previous.photo == uploaded) { "这条记录已发布，请刷新后查看。" }
                state.copy(privateCommunity = state.privateCommunity + (recipeId to current.copy(
                    posts = if (previous != null) current.posts else current.posts + post)))
            }
        } else {
            if (photo != null) api.request(route(recipeId) + "/uploads/$postId", "PUT", expected = id, bytes = media.jpeg(photo, true))
            val body = buildJsonObject { put("id", postId); put("text", text.trim()); put("submission", submission); put("photo", photo != null) }
            try { api.request(route(recipeId) + "/posts", "POST", body, id) }
            catch (error: CloudException) {
                if (error.status != 0) throw error
                api.request(route(recipeId) + "/posts", "POST", body, id)
            }
            read(recipeId, id, "${id}_$recipeId")
        }
        postId
    }
    override suspend fun deletePost(id: String): String? = mutex.withLock {
        val privateRecipe = kitchen.state.value.privateCommunity.entries.firstOrNull { (_, content) -> content.posts.any { it.id == id } }?.key
        if (privateRecipe != null) kitchen.mutate { state ->
            val current = state.privateCommunity[privateRecipe] ?: PrivateCommunity()
            state.copy(privateCommunity = state.privateCommunity + (privateRecipe to current.copy(posts = current.posts.filterNot { it.id == id })))
        } else {
            val identity = expected()
            val record = cache.filterKeys { it.startsWith(identity + "_") }.values
                .firstOrNull { it.posts.any { post -> post.id == id && post.mine } } ?: throw CloudException(403, "只能删除自己的评论或作业。")
            api.request(route(record.recipeId) + "/posts/$id", "DELETE", expected = identity)
            read(record.recipeId, identity, "${identity}_${record.recipeId}")
        }
        null
    }
    override suspend fun more(recipeId: String, submission: Boolean) = mutex.withLock {
        if (recipeId.startsWith(USER_RECIPE_ID_PREFIX)) return@withLock
        val id = expected()
        val key = "${id}_$recipeId"
        val current = cache[key] ?: return@withLock
        val cursor = (if (submission) current.cursors.submission else current.cursors.comment) ?: return@withLock
        val path = route(recipeId) + "/posts?kind=" + (if (submission) "submission" else "comment") +
            "&cursor=" + java.net.URLEncoder.encode(cursor, "UTF-8")
        val page = json.decodeFromJsonElement<WirePage>(api.request(path, expected = id))
        val cursors = if (submission) current.cursors.copy(submission = page.next) else current.cursors.copy(comment = page.next)
        save(key, current.copy(posts = (current.posts + page.posts).distinctBy { it.id }, cursors = cursors))
    }
    /** Import only actual local-user records. Never upload the bundled example rows. */
    suspend fun importLocalSocial() {
        val journal = File(folder.parentFile, "cloud-social-imports.json")
        val completed = withContext(Dispatchers.IO) {
            if (journal.exists()) json.decodeFromString<Set<String>>(journal.readText()).toMutableSet() else mutableSetOf()
        }
        suspend fun done(key: String) = withContext(Dispatchers.IO) {
            completed.add(key)
            journal.parentFile?.mkdirs()
            val staged = File(journal.parentFile, journal.name + ".pending")
            staged.outputStream().use { it.write(json.encodeToString(completed.toSet()).encodeToByteArray()); it.fd.sync() }
            check(staged.renameTo(journal)) { "迁移记录无法保存，原件已保留。" }
        }
        val ratings = withContext(Dispatchers.IO) {
            local.database.readableDatabase.rawQuery("SELECT recipe_id,score FROM ratings WHERE user_key='local'", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1)) }
            }
        }
        for ((recipe, score) in ratings) {
            val marker = "rating:$recipe"
            if (marker in completed) continue
            val existing = if (recipe.startsWith(USER_RECIPE_ID_PREFIX)) kitchen.state.value.privateCommunity[recipe]?.score
                else json.decodeFromJsonElement<WireCommunity>(api.request(route(recipe), expected = expected())).ownRating
            if (existing == null) rate(recipe, score)
            done(marker)
        }
        val posts = withContext(Dispatchers.IO) {
            local.database.readableDatabase.rawQuery("SELECT id,recipe_id,submission,text,photo,created_at FROM posts WHERE sample=0 ORDER BY created_at,id", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(KitchenPost(cursor.getString(0), cursor.getString(1), cursor.getInt(2) == 1,
                    cursor.getString(3), cursor.getString(4), "我", false, cursor.getLong(5))) }
            }
        }
        for (entry in posts) {
            val marker = "post:${entry.id}"
            if (marker in completed) continue
            val raw = java.security.MessageDigest.getInstance("SHA-256").digest(("deepcook-android-local:" + entry.id).toByteArray()).copyOf(16)
            raw[6] = ((raw[6].toInt() and 15) or 64).toByte()
            raw[8] = ((raw[8].toInt() and 63) or 128).toByte()
            val buffer = java.nio.ByteBuffer.wrap(raw)
            val stableId = "post-${UUID(buffer.long, buffer.long)}"
            post(entry.recipeId, entry.submission, entry.text, entry.photo, stableId)
            done(marker)
        }
    }

    override suspend fun discard(recipeId: String, draftId: String) {
        if (!recipeId.startsWith(USER_RECIPE_ID_PREFIX)) api.request(route(recipeId) + "/uploads/$draftId", "DELETE", expected = expected())
    }
}
