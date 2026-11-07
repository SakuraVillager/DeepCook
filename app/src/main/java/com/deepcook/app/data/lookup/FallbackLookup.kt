package com.deepcook.app.data.lookup

/**
 * Runs [primary] first and asks [fallback] only when nothing usable came back.
 *
 * The order is deliberate and fixed: bundled catalogue → pinned HowToCook page → model → the user's
 * own typing. An offline result never reaches the model, because it would need the same network.
 */
class FallbackLookup(private val primary: RecipeLookup, private val fallback: RecipeLookup) : RecipeLookup {
    override suspend fun lookup(name: String): LookupOutcome {
        val first = primary.lookup(name)
        if (first is LookupOutcome.Filled || first is LookupOutcome.Offline) return first
        return when (val second = fallback.lookup(name)) {
            is LookupOutcome.Filled -> second
            LookupOutcome.Offline -> second
            // Nothing anywhere: the first answer ("库里没有") is the more specific one.
            LookupOutcome.NotFound -> first
            // A failed model call explains itself; the catalogue's "not found" does not.
            is LookupOutcome.Failed -> second
        }
    }
}
