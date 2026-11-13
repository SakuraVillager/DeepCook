package com.deepcook.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.deepcook.app.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Separate from the existing DataStores: ratings, posts and favourite examples are transactional. */
class KitchenDatabase(private val context: Context, name: String = "kitchen.db") :
    SQLiteOpenHelper(
        context,
        name,
        null,
        2,
        android.database.DatabaseErrorHandler {
            throw android.database.sqlite.SQLiteDatabaseCorruptException("本地数据库损坏，原文件已保留")
        },
    ) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    private val nutritionCatalog by lazy {
        NutritionCatalog.decode(context.assets.open("nutrition_estimates.json").bufferedReader().use { it.readText() })
    }
    private val bundledRecipes by lazy {
        context.assets.open("recipes.json").bufferedReader().use {
            json.decodeFromString<List<Recipe>>(it.readText()).associateBy { recipe -> recipe.id }
        }
    }
    fun nutritionFor(id: String): Nutrition? = bundledRecipes[id]?.let(nutritionCatalog::estimate)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE recipe_social(recipe_id TEXT PRIMARY KEY, favorites INTEGER NOT NULL, nutrition TEXT)")
        db.execSQL(
            "CREATE TABLE ratings(recipe_id TEXT, user_key TEXT, score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 10), PRIMARY KEY(recipe_id,user_key))",
        )
        db.execSQL(
            "CREATE TABLE posts(id TEXT PRIMARY KEY, recipe_id TEXT NOT NULL, submission INTEGER NOT NULL, text TEXT NOT NULL, photo TEXT, author TEXT NOT NULL, sample INTEGER NOT NULL, created_at INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX posts_recipe ON posts(recipe_id,created_at DESC)")
        // Local fixtures can be replaced by a content database. No food facts are inferred.
        val recipes = context.assets.open("recipes.json").bufferedReader().use {
            json.decodeFromString<List<Recipe>>(it.readText())
        }
        recipes.forEachIndexed { index, recipe ->
            val example = index < 12 || recipe.featured
            val nutrition = nutritionCatalog.estimate(recipe)
            db.insertOrThrow(
                "recipe_social",
                null,
                ContentValues().apply {
                    put("recipe_id", recipe.id)
                    put("favorites", 12 + index % 89)
                    put("nutrition", nutrition?.let { json.encodeToString(it) })
                },
            )
            if (example) {
                listOf(8, 9, 6).forEachIndexed { n, score ->
                    db.execSQL("INSERT INTO ratings VALUES(?,?,?)", arrayOf<Any>(recipe.id, "sample-$n", score))
                }
                db.insertOrThrow(
                    "posts",
                    null,
                    postValues(
                        KitchenPost(
                            "example-comment-${recipe.id}",
                            recipe.id,
                            false,
                            "下次想试试这个做法。",
                            null,
                            "小禾",
                            true,
                            1_700_000_000_000,
                        ),
                    ),
                )
                db.insertOrThrow(
                    "posts",
                    null,
                    postValues(
                        KitchenPost(
                            "example-work-${recipe.id}",
                            recipe.id,
                            true,
                            "第一次尝试，记录一下这次的成品。",
                            recipe.cover,
                            "阿米",
                            true,
                            1_700_000_000_001,
                        ),
                    ),
                )
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            listOf(
                "author" to ("示例用户 · 小禾" to "小禾"),
                "author" to ("示例用户 · 阿米" to "阿米"),
                "text" to ("示例评论：下次想试试这个做法。" to "下次想试试这个做法。"),
                "text" to ("示例作业：这里可以记录成品照片和下厨心得。配图借用菜谱封面。" to "第一次尝试，记录一下这次的成品。"),
            ).forEach { (column, values) ->
                db.update(
                    "posts",
                    ContentValues().apply {
                        put(column, values.second)
                    },
                    "$column=? AND sample=1",
                    arrayOf(values.first),
                )
            }
        } else {
            error("需要从 $oldVersion 到 $newVersion 的数据库迁移")
        }
    }
}

private fun postValues(post: KitchenPost) = ContentValues().apply {
    put("id", post.id)
    put("recipe_id", post.recipeId)
    put("submission", if (post.submission) 1 else 0)
    put("text", post.text)
    put("photo", post.photo)
    put("author", post.author)
    put("sample", if (post.sample) 1 else 0)
    put("created_at", post.createdAt)
}

private fun Cursor.text(name: String) = getString(getColumnIndexOrThrow(name))

interface KitchenCommunity {
    val cloud: Boolean get() = false
    val accountId: String? get() = null
    val favoriteExamples: kotlinx.coroutines.flow.Flow<Map<String, Int>>
    fun observe(recipeId: String): kotlinx.coroutines.flow.Flow<RecipeCommunity>
    suspend fun rate(recipeId: String, score: Int)
    suspend fun post(recipeId: String, submission: Boolean, text: String, photo: String?, draftId: String? = null): String
    suspend fun deletePost(id: String): String?
    suspend fun more(recipeId: String, submission: Boolean) {}
    suspend fun discard(recipeId: String, draftId: String) {}
    fun invalidate() {}
}

class KitchenStore(val database: KitchenDatabase) : KitchenCommunity {
    private val changes = MutableStateFlow(0L)
    private val mutex = Mutex()
    override val favoriteExamples = changes.map {
        database.readableDatabase.rawQuery("SELECT recipe_id,favorites FROM recipe_social", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override fun observe(recipeId: String) = changes.map { readCommunity(recipeId) }.flowOn(Dispatchers.IO)

    private fun readCommunity(id: String): RecipeCommunity {
        val db = database.readableDatabase
        val nutrition = database.nutritionFor(id)
        var favorites = 0
        db.rawQuery("SELECT favorites,nutrition FROM recipe_social WHERE recipe_id=?", arrayOf(id)).use {
            if (it.moveToFirst()) {
                favorites = it.getInt(0)
            }
        }
        var own: Int? = null
        val ratings = db.rawQuery("SELECT user_key,score FROM ratings WHERE recipe_id=?", arrayOf(id)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(c.getInt(1))
                    if (c.getString(0) == "local") own = c.getInt(1)
                }
            }
        }
        val posts = db.rawQuery(
            "SELECT * FROM posts WHERE recipe_id=? ORDER BY created_at DESC,id DESC",
            arrayOf(id),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        KitchenPost(
                            c.text("id"),
                            id,
                            c.getInt(c.getColumnIndexOrThrow("submission")) == 1,
                            c.text("text"),
                            c.text("photo"),
                            c.text("author"),
                            c.getInt(c.getColumnIndexOrThrow("sample")) == 1,
                            c.getLong(c.getColumnIndexOrThrow("created_at")),
                        ),
                    )
                }
            }
        }
        return RecipeCommunity(nutrition, favorites, ratings, own, posts)
    }

    override suspend fun rate(recipeId: String, score: Int) = mutate {
        require(score in 1..10)
        database.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO ratings VALUES(?,?,?)",
            arrayOf<Any>(recipeId, "local", score),
        )
    }

    override suspend fun post(recipeId: String, submission: Boolean, text: String, photo: String?, draftId: String?): String {
        require(text.isNotBlank() && text.length <= 2000)
        val id = draftId ?: UUID.randomUUID().toString()
        mutate {
            database.writableDatabase.insertOrThrow(
                "posts",
                null,
                postValues(
                    KitchenPost(
                        id,
                        recipeId,
                        submission,
                        text.trim(),
                        photo,
                        "我",
                        false,
                        System.currentTimeMillis(),
                    ),
                ),
            )
        }
        return id
    }

    override suspend fun deletePost(id: String): String? {
        var photo: String? = null
        mutate {
            database.writableDatabase.beginTransaction()
            try {
                database.writableDatabase.rawQuery("SELECT photo FROM posts WHERE id=? AND sample=0", arrayOf(id)).use {
                    if (it.moveToFirst()) photo = it.getString(0)
                }
                database.writableDatabase.delete("posts", "id=? AND sample=0", arrayOf(id))
                database.writableDatabase.setTransactionSuccessful()
            } finally {
                database.writableDatabase.endTransaction()
            }
        }
        return photo
    }

    private suspend fun mutate(action: () -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            action()
            changes.value++
            Unit
        }
    }
}
