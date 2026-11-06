package com.deepcook.app.core.model

import kotlinx.serialization.Serializable

/**
 * Fields the assistant looked up because the bundled recipe left them empty. This never touches
 * `recipes.json`; it is a per-dish cache that only feeds the assistant's context, and the user
 * can clear it in Settings.
 */
@Serializable
data class AiOverlay(
    val recipeId: String,
    val minutes: Int? = null,
    val difficulty: Int? = null,
    val servings: String? = null,
    /** Ingredient id to a concrete amount, for entries the recipe only calls "适量". */
    val amounts: Map<String, String> = emptyMap(),
    val fetchedAt: Long = 0,
)

@Serializable
data class AiOverlayFile(val entries: Map<String, AiOverlay> = emptyMap())
