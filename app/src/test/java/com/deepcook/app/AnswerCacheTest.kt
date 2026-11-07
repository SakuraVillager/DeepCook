package com.deepcook.app

import com.deepcook.app.feature.cooking.AnswerCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerCacheTest {
    @Test fun spacingAndPunctuationDoNotMakeANewQuestion() {
        val cache = AnswerCache()
        val key = cache.key(2, "盐放多少？")
        cache.put(key, "两克")
        assertEquals("两克", cache.get(cache.key(2, " 盐放多少 ")))
        assertEquals("两克", cache.get(cache.key(2, "盐放多少。")))
    }

    @Test fun theSameQuestionOnAnotherStepIsADifferentEntry() {
        val cache = AnswerCache()
        cache.put(cache.key(1, "还要多久"), "五分钟")
        assertNull(cache.get(cache.key(4, "还要多久")))
    }

    @Test fun theCacheIsBoundedAndDropsTheOldestEntry() {
        val cache = AnswerCache(limit = 2)
        cache.put(cache.key(0, "a"), "1")
        cache.put(cache.key(0, "b"), "2")
        cache.put(cache.key(0, "c"), "3")
        assertNull(cache.get(cache.key(0, "a")))
        assertEquals("3", cache.get(cache.key(0, "c")))
    }

    @Test fun anEmptyAnswerIsNotRemembered() {
        val cache = AnswerCache()
        cache.put(cache.key(0, "q"), "   ")
        assertNull(cache.get(cache.key(0, "q")))
    }
}
