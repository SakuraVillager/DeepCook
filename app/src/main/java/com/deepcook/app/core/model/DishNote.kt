package com.deepcook.app.core.model

import kotlinx.serialization.Serializable

/** Who wrote a dish note: the user's own words, or the model's one-line rewrite of them. */
@Serializable
enum class NoteSource(val label: String) { USER("手动"), AI("AI 整理") }

/**
 * A correction for one dish, remembered for the next time it is cooked.
 *
 * [text] is the short, actionable form shown and spoken ("下次少放盐"); [raw] keeps what the user
 * actually said so a bad rewrite can be traced. These follow the signed-in account, with a separate local cache for each account.
 */
@Serializable
data class DishNote(
    val id: String,
    val recipeId: String,
    val text: String,
    val raw: String = "",
    val updatedAt: Long = 0,
    val source: NoteSource = NoteSource.USER,
    /** Reserved for notes attached to one step; the current UI writes dish-level notes only. */
    val stepId: String? = null,
)
