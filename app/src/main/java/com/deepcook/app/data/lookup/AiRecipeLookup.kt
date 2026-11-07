package com.deepcook.app.data.lookup

import com.deepcook.app.core.model.AI_DRAFT_SOURCE
import com.deepcook.app.data.ai.AiGateway
import com.deepcook.app.data.ai.AiMessage
import kotlinx.coroutines.CancellationException

/**
 * The last step before the form stays manual: one model reply, read as a draft.
 *
 * It runs only after the bundled catalogue and the pinned HowToCook index both came up empty, so
 * an answer this project reviewed always wins. What comes back is a [com.deepcook.app.core.model.RecipeDraft]
 * — editable, clearly marked as a model's draft, and never written into the recipe assets.
 */
class AiRecipeLookup(private val ai: AiGateway, private val network: NetworkStatus) : RecipeLookup {
    override suspend fun lookup(name: String): LookupOutcome {
        val query = name.trim()
        if (query.isEmpty()) return LookupOutcome.NotFound
        if (!network.isOnline()) return LookupOutcome.Offline

        val reply = try {
            ai.complete(
                listOf(
                    AiMessage.system(AiRecipeParser.SYSTEM),
                    AiMessage.user(AiRecipeParser.prompt(query)),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A failed request is not a reason to lose the form; the caller keeps it manual.
            null
        } ?: return LookupOutcome.Failed("AI 暂时没有回应（可能正忙），可以重试，或者手动填写。")

        val draft = AiRecipeParser.parse(query, reply) ?: return LookupOutcome.NotFound
        return LookupOutcome.Filled(
            draft = draft,
            matchedName = query,
            // No page was read, so there is no link to show; the licence line says it is a draft.
            sourceUrl = null,
            sourceName = AI_DRAFT_SOURCE,
        )
    }
}
