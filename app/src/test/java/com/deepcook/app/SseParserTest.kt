package com.deepcook.app

import com.deepcook.app.data.ai.SseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {
    @Test fun aContentFragmentIsExtracted() {
        val line =
            """data: {"id":"x","choices":[{"index":0,"delta":{"content":"先关火"}}]}"""
        assertEquals("先关火", SseParser.delta(line))
    }

    @Test fun roleOnlyAndFinishChunksCarryNoText() {
        assertNull(SseParser.delta("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))
        assertNull(SseParser.delta("""data: {"choices":[{"delta":{},"finish_reason":"stop"}]}"""))
    }

    @Test fun theDoneMarkerAndBlankLinesAreIgnored() {
        assertNull(SseParser.delta("data: [DONE]"))
        assertNull(SseParser.delta("data:"))
        assertNull(SseParser.delta(""))
    }

    @Test fun malformedPayloadsDoNotThrow() {
        assertNull(SseParser.delta("data: not json"))
        assertNull(SseParser.delta("data: []"))
        assertNull(SseParser.delta("data: {\"choices\":\"oops\"}"))
    }

    @Test fun nonDataLinesAreIgnored() {
        assertNull(SseParser.delta("event: ping"))
        assertNull(SseParser.delta(": keep-alive"))
    }
}
