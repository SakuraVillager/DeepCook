package com.deepcook.app

import com.deepcook.app.data.ai.NoteSuggester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteSuggesterTest {
    @Test fun theFirstLineIsKeptAndQuotesAreStripped() {
        assertEquals("下次少放盐", NoteSuggester.clean("「下次少放盐」\n因为这次偏咸。"))
        assertEquals("下次少放盐", NoteSuggester.clean("\"下次少放盐\""))
    }

    @Test fun aBlankAnswerBecomesNull() {
        assertNull(NoteSuggester.clean("   \n  "))
    }

    @Test fun aLongAnswerIsCappedAtTwentyCharacters() {
        val cleaned = NoteSuggester.clean("盐".repeat(50))!!
        assertEquals(20, cleaned.length)
    }
}
