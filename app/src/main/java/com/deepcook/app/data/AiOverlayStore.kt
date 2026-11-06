package com.deepcook.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.deepcook.app.core.model.AiOverlay
import com.deepcook.app.core.model.AiOverlayFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream

/**
 * Per-dish assistant lookups (missing minutes, difficulty, concrete amounts), cached on this
 * device. Kept apart from the recipe assets so AI output can never change the catalog.
 */
interface AiOverlayStore {
    val entries: Flow<Map<String, AiOverlay>>
    suspend fun get(recipeId: String): AiOverlay?
    suspend fun put(overlay: AiOverlay)
    suspend fun clear()
}

object AiOverlaySerializer : Serializer<AiOverlayFile> {
    override val defaultValue = AiOverlayFile()

    override suspend fun readFrom(input: InputStream): AiOverlayFile = try {
        json.decodeFromString<AiOverlayFile>(input.readBytes().decodeToString())
    } catch (error: SerializationException) {
        // A broken cache is disposable; the catalog and the user's own data are not.
        throw CorruptionException("AI 补充数据无法读取", error)
    }

    override suspend fun writeTo(t: AiOverlayFile, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

class LocalAiOverlayStore(private val store: DataStore<AiOverlayFile>) : AiOverlayStore {
    override val entries: Flow<Map<String, AiOverlay>> = store.data.map { it.entries }

    override suspend fun get(recipeId: String): AiOverlay? = store.data.first().entries[recipeId]

    override suspend fun put(overlay: AiOverlay) {
        store.updateData { it.copy(entries = it.entries + (overlay.recipeId to overlay)) }
    }

    override suspend fun clear() {
        store.updateData { AiOverlayFile() }
    }
}
