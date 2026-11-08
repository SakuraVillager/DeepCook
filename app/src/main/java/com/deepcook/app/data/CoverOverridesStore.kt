package com.deepcook.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.deepcook.app.core.model.CoverOverrideFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream

/**
 * Per-dish cover photos chosen by the user, kept apart from the recipe assets.
 *
 * Only the user's own data is written here: the bundled catalog stays untouched, and deleting the
 * file simply brings back the shipped pictures.
 */
interface CoverOverrides {
    val entries: Flow<Map<String, String>>
    suspend fun put(recipeId: String, cover: String)
    suspend fun remove(recipeId: String)
}

object CoverOverrideSerializer : Serializer<CoverOverrideFile> {
    override val defaultValue = CoverOverrideFile()

    override suspend fun readFrom(input: InputStream): CoverOverrideFile = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (error: SerializationException) {
        // A broken override file is disposable; the catalog and user recipes are not.
        throw CorruptionException("封面记录无法读取", error)
    }

    override suspend fun writeTo(t: CoverOverrideFile, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

class LocalCoverOverrides(private val store: DataStore<CoverOverrideFile>) : CoverOverrides {
    override val entries: Flow<Map<String, String>> = store.data.map { it.entries }

    override suspend fun put(recipeId: String, cover: String) {
        store.updateData { it.copy(entries = it.entries + (recipeId to cover)) }
    }

    override suspend fun remove(recipeId: String) {
        store.updateData { it.copy(entries = it.entries - recipeId) }
    }
}
