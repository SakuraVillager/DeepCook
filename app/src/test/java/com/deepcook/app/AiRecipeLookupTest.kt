package com.deepcook.app

import com.deepcook.app.core.model.AI_DRAFT_SOURCE
import com.deepcook.app.core.model.DraftIngredient
import com.deepcook.app.core.model.DraftStep
import com.deepcook.app.core.model.RecipeDraft
import com.deepcook.app.data.ai.AiEvent
import com.deepcook.app.data.ai.AiGateway
import com.deepcook.app.data.ai.AiMessage
import com.deepcook.app.data.lookup.AiRecipeLookup
import com.deepcook.app.data.lookup.FallbackLookup
import com.deepcook.app.data.lookup.LookupOutcome
import com.deepcook.app.data.lookup.NetworkStatus
import com.deepcook.app.data.lookup.RecipeLookup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecipeLookupTest {
    private val reply = """
        {"found": true, "region": "中式", "categories": ["家常菜"], "minutes": 40, "difficulty": 2,
         "servings": "2 人份", "ingredients": [{"name": "五花肉", "amount": "500 克"}],
         "steps": [{"title": "焯水", "text": "冷水下锅焯三分钟。"}]}
    """.trimIndent()

    private class FakeAi(private val reply: String?) : AiGateway {
        var calls = 0
            private set

        override fun stream(messages: List<AiMessage>, model: String?): Flow<AiEvent> = emptyFlow()

        override suspend fun complete(messages: List<AiMessage>): String? {
            calls++
            return reply
        }
    }

    @Test fun offlineSkipsTheModel() = runBlocking {
        val ai = FakeAi(reply)
        assertEquals(LookupOutcome.Offline, AiRecipeLookup(ai, NetworkStatus { false }).lookup("红烧肉"))
        assertEquals(0, ai.calls)
    }

    @Test fun aUsableReplyBecomesAnAiDraft() = runBlocking {
        val ai = FakeAi(reply)
        val filled = AiRecipeLookup(ai, NetworkStatus { true }).lookup("红烧肉") as LookupOutcome.Filled
        assertEquals(AI_DRAFT_SOURCE, filled.sourceName)
        assertNull(filled.sourceUrl)
        assertEquals("红烧肉", filled.draft.title)
        // The prompt asks for blank amounts; anything measured is dropped on the way in anyway.
        assertEquals("", filled.draft.ingredients.single().amount)
        assertEquals(1, ai.calls)
    }

    @Test fun noReplyIsAFailureNotAnEmptyForm() = runBlocking {
        val outcome = AiRecipeLookup(FakeAi(null), NetworkStatus { true }).lookup("红烧肉")
        assertTrue(outcome is LookupOutcome.Failed)
        assertTrue((outcome as LookupOutcome.Failed).message.contains("手动填写"))
    }

    @Test fun aRefusalIsNotFound() = runBlocking {
        val outcome = AiRecipeLookup(FakeAi("""{"found": false}"""), NetworkStatus { true }).lookup("不是菜")
        assertEquals(LookupOutcome.NotFound, outcome)
    }
}

class FallbackLookupTest {
    private class Fixed(private val outcome: LookupOutcome) : RecipeLookup {
        var calls = 0
            private set

        override suspend fun lookup(name: String): LookupOutcome {
            calls++
            return outcome
        }
    }

    private fun draft(name: String) =
        LookupOutcome.Filled(RecipeDraft(title = name), name, null, AI_DRAFT_SOURCE)

    @Test fun aLocalMatchNeverAsksTheModel() = runBlocking {
        val ai = Fixed(draft("红烧肉"))
        val outcome = FallbackLookup(Fixed(draft("红烧肉")), ai).lookup("红烧肉")
        assertTrue(outcome is LookupOutcome.Filled)
        assertEquals(0, ai.calls)
    }

    @Test fun offlineDoesNotAskTheModel() = runBlocking {
        val ai = Fixed(draft("某菜"))
        assertEquals(LookupOutcome.Offline, FallbackLookup(Fixed(LookupOutcome.Offline), ai).lookup("某菜"))
        assertEquals(0, ai.calls)
    }

    @Test fun aMissFallsThroughToTheModel() = runBlocking {
        val ai = Fixed(draft("某菜"))
        val outcome = FallbackLookup(Fixed(LookupOutcome.NotFound), ai).lookup("某菜") as LookupOutcome.Filled
        assertEquals("某菜", outcome.draft.title)
        assertEquals(1, ai.calls)
    }

    @Test fun aModelMissKeepsTheCatalogueAnswer() = runBlocking {
        val outcome = FallbackLookup(Fixed(LookupOutcome.NotFound), Fixed(LookupOutcome.NotFound)).lookup("某菜")
        assertEquals(LookupOutcome.NotFound, outcome)
    }

    @Test fun aModelFailureIsTheMessageTheUserSees() = runBlocking {
        val outcome = FallbackLookup(Fixed(LookupOutcome.NotFound), Fixed(LookupOutcome.Failed("AI 正忙")))
            .lookup("某菜")
        assertEquals("AI 正忙", (outcome as LookupOutcome.Failed).message)
    }
}
