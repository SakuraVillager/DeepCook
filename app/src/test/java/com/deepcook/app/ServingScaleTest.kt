package com.deepcook.app

import com.deepcook.app.data.ai.ServingScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServingScaleTest {
    @Test fun thePrintedServingCountIsRead() {
        assertEquals(2.0, ServingScale.parse("2 人份")!!, 0.001)
        assertEquals(2.0, ServingScale.parse("原始份量 · 2—3 人份")!!, 0.001)
        assertEquals(1.0, ServingScale.parse("一 人份")!!, 0.001)
        assertEquals(8.0, ServingScale.parse("原始份量 · 8 人份")!!, 0.001)
    }

    @Test fun anUnlabelledDishHasNoBase() {
        assertNull(ServingScale.parse("无"))
        assertNull(ServingScale.parse(""))
    }

    @Test fun theLabelDropsTheImportedMarker() {
        assertEquals("2 人份", ServingScale.label("原始份量 · 2 人份"))
        assertEquals("2 人份", ServingScale.label(" 2 人份 "))
    }

    @Test fun amountsScaleAndKeepTheirUnit() {
        assertEquals("250 克", ServingScale.scale("500 克", 0.5))
        assertEquals("2.5g", ServingScale.scale("5g", 0.5))
        assertEquals("约 125g", ServingScale.scale("约 250g", 0.5))
        assertEquals("1/4 茶匙", ServingScale.scale("1/2 茶匙", 0.5))
        assertEquals("1/2 个", ServingScale.scale("1 个", 0.5))
        assertEquals("1.5~2 个", ServingScale.scale("3~4 个", 0.5))
    }

    @Test fun placeholderAndRelativeAmountsStayUntouched() {
        assertEquals("适量", ServingScale.scale("适量", 0.5))
        assertEquals("少许", ServingScale.scale("少许", 2.0))
        assertEquals("200 克  * 份数", ServingScale.scale("200 克  * 份数", 0.5))
        assertEquals("半个", ServingScale.scale("半个", 2.0))
        assertEquals("1 个（约 80g）", ServingScale.scale("1 个（约 80g）", 2.0))
    }

    @Test fun aFactorOfOneIsAnExactCopy() {
        assertEquals("500 克", ServingScale.scale("500 克", 1.0))
    }

    @Test fun optionsAlwaysContainThePrintedSize() {
        assertTrue(ServingScale.options(5.0).contains(5))
        assertEquals(listOf(1, 2, 3, 4, 6, 8), ServingScale.options(2.0))
    }
}
