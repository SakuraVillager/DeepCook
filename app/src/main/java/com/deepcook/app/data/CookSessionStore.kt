package com.deepcook.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.deepcook.app.core.model.CookSession
import com.deepcook.app.core.model.CookSessionFile
import com.deepcook.app.core.model.CookTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * The dish that is currently being cooked, in its own file.
 *
 * It is separate from the recipe content: only the position in the steps and the running timers
 * live here, so nothing the assistant or the user records can touch `assets/recipes.json`.
 */
interface CookSessionStore {
    val session: Flow<CookSession?>

    /**
     * Resumes [recipeId] where it stopped. Opening a different dish replaces the stored session,
     * because the cooking screen and its notification show a single dish at a time.
     */
    suspend fun ensure(recipeId: String, recipeTitle: String, now: Long = System.currentTimeMillis()): CookSession

    suspend fun setStep(recipeId: String, index: Int)

    suspend fun addTimer(recipeId: String, minutes: Int, now: Long = System.currentTimeMillis()): CookTimer

    suspend fun removeTimer(timerId: String)

    suspend fun clear()
}

object CookSessionSerializer : Serializer<CookSessionFile> {
    override val defaultValue = CookSessionFile()

    override suspend fun readFrom(input: InputStream): CookSessionFile = try {
        json.decodeFromString<CookSessionFile>(input.readBytes().decodeToString())
    } catch (error: SerializationException) {
        // A broken progress file must never take the catalog down with it.
        throw CorruptionException("做菜进度无法读取", error)
    }

    override suspend fun writeTo(t: CookSessionFile, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

class LocalCookSessionStore(private val store: DataStore<CookSessionFile>) : CookSessionStore {
    override val session: Flow<CookSession?> = store.data.map { it.session }

    override suspend fun ensure(recipeId: String, recipeTitle: String, now: Long): CookSession {
        val updated = store.updateData { file ->
            val current = file.session
            if (current != null && current.recipeId == recipeId) {
                file.copy(
                    session = current.copy(recipeTitle = recipeTitle.ifBlank { current.recipeTitle }),
                )
            } else {
                CookSessionFile(CookSession(recipeId = recipeId, recipeTitle = recipeTitle, startedAt = now))
            }
        }
        return requireNotNull(updated.session) { "做菜进度写入失败" }
    }

    override suspend fun setStep(recipeId: String, index: Int) {
        store.updateData { file ->
            val current = file.session
            if (current == null || current.recipeId != recipeId) {
                file
            } else {
                file.copy(session = current.copy(stepIndex = index.coerceAtLeast(0)))
            }
        }
    }

    override suspend fun addTimer(recipeId: String, minutes: Int, now: Long): CookTimer {
        val timer = CookTimer(
            id = UUID.randomUUID().toString(),
            label = "$minutes 分钟",
            endsAt = now + minutes * 60_000L,
            minutes = minutes,
        )
        store.updateData { file ->
            val current = file.session
            if (current == null || current.recipeId != recipeId) {
                file
            } else {
                file.copy(session = current.copy(timers = current.timers + timer))
            }
        }
        return timer
    }

    override suspend fun removeTimer(timerId: String) {
        store.updateData { file ->
            val current = file.session ?: return@updateData file
            file.copy(session = current.copy(timers = current.timers.filterNot { it.id == timerId }))
        }
    }

    override suspend fun clear() {
        store.updateData { CookSessionFile(null) }
    }
}
