package com.deepcook.app.data.lookup

import com.deepcook.app.core.model.RecipeDraft

/** What happened when the app tried to prefill a recipe from the online catalogue. */
sealed interface LookupOutcome {
    /** A match was parsed; [draft] carries every field the source provided. */
    data class Filled(
        val draft: RecipeDraft,
        val matchedName: String,
        val sourceUrl: String,
        val sourceName: String,
    ) : LookupOutcome

    /** The catalogue was reachable but has no dish by that name. */
    data object NotFound : LookupOutcome

    /** No usable network right now — the form simply stays manual. */
    data object Offline : LookupOutcome

    /** Network or parsing failed; the caller shows [message] and keeps the manual form. */
    data class Failed(val message: String) : LookupOutcome
}

/**
 * Looks a dish up by name and returns a prefilled draft.
 *
 * Implementations must never throw for ordinary conditions: "not found", "offline" and
 * "request failed" are outcomes, because none of them should stop the user from typing the
 * recipe out by hand.
 */
interface RecipeLookup {
    suspend fun lookup(name: String): LookupOutcome
}
