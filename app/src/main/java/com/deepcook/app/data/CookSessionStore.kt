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

    /** A whole-minute timer, the shape the voice command 「计时十分钟」 produces. */
    suspend fun addTimer(recipeId: String, minutes: Int, now: Long = System.currentTimeMillis()): CookTimer

    /**
     * A timer with an arbitrary length and an optional step, used by the reader's per-step chips.
     * [label] is what the notification and the list row show instead of "N 分钟".
     */
    suspend fun addTimer(
        recipeId: String,
        seconds: Long,
        label: String,
        stepId: String?,
        now: Long = System.currentTimeMillis(),
    ): CookTimer

    /** Freezes the countdown and remembers what was left; a paused timer never rings. */
    suspend fun pauseTimer(timerId: String, now: Long = System.currentTimeMillis())

    /** Restarts a paused timer from the value it was paused at. */
    suspend fun resumeTimer(timerId: String, now: Long = System.currentTimeMillis())

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

    override suspend fun addTimer(recipeId: String, minutes: Int, now: Long): CookTimer =
        addTimer(recipeId, minutes * 60L, "$minutes 分钟", null, now)

    override suspend fun addTimer(
        recipeId: String,
        seconds: Long,
        label: String,
        stepId: String?,
        now: Long,
    ): CookTimer {
        val timer = CookTimer(
            id = UUID.randomUUID().toString(),
            label = label.ifBlank { "${(seconds / 60).coerceAtLeast(1)} 分钟" },
            endsAt = now + seconds * 1000L,
            minutes = ((seconds + 59) / 60).toInt().coerceAtLeast(1),
            stepId = stepId,
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

    override suspend fun pauseTimer(timerId: String, now: Long) {
        store.updateData { file ->
            val current = file.session ?: return@updateData file
            file.copy(
                session = current.copy(
                    timers = current.timers.map { timer ->
                        if (timer.id != timerId || timer.paused) {
                            timer
                        } else {
                            timer.copy(pausedRemainingMs = (timer.endsAt - now).coerceAtLeast(0))
                        }
                    },
                ),
            )
        }
    }

    override suspend fun resumeTimer(timerId: String, now: Long) {
        store.updateData { file ->
            val current = file.session ?: return@updateData file
            file.copy(
                session = current.copy(
                    timers = current.timers.map { timer ->
                        val left = timer.pausedRemainingMs
                        if (timer.id != timerId || left == null) {
                            timer
                        } else {
                            timer.copy(endsAt = now + left, pausedRemainingMs = null)
                        }
                    },
                ),
            )
        }
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
