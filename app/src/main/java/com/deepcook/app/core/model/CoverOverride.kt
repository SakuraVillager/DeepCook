package com.deepcook.app.core.model

import kotlinx.serialization.Serializable

/**
 * Cover photos the user took that shadow a bundled recipe's shipped picture.
 *
 * The catalog is read-only, so a finished-dish photo cannot be written into `recipes.json`; it
 * lives beside the user's other data instead. User-authored recipes carry their own cover and are
 * never shadowed — their record is the single source for that image.
 */
@Serializable
data class CoverOverrideFile(val entries: Map<String, String> = emptyMap())

/** Applies saved cover photos over the bundled catalog; blank entries change nothing. */
fun List<Recipe>.withCoverOverrides(overrides: Map<String, String>): List<Recipe> = map { recipe ->
    if (recipe.isMine) {
        recipe
    } else {
        overrides[recipe.id]?.takeIf { it.isNotBlank() }?.let { recipe.copy(cover = it) } ?: recipe
    }
}
