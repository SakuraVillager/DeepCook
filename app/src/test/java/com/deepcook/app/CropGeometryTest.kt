package com.deepcook.app

import com.deepcook.app.feature.profile.CropGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGeometryTest {
    @Test fun atRestTheSquareIsTheShortestSideCentred() {
        val rect = CropGeometry.rect(1000, 800, 1f, 0f, 0f, 400f)
        assertEquals(100, rect[0])
        assertEquals(0, rect[1])
        assertEquals(800, rect[2])
    }

    @Test fun aPortraitPhotoIsCroppedAroundItsMiddle() {
        val rect = CropGeometry.rect(600, 1200, 1f, 0f, 0f, 400f)
        assertEquals(0, rect[0])
        assertEquals(300, rect[1])
        assertEquals(600, rect[2])
    }

    @Test fun zoomingInHalvesTheSquareAroundTheSameCentre() {
        val rect = CropGeometry.rect(1000, 800, 2f, 0f, 0f, 400f)
        assertEquals(400, rect[2])
        assertEquals(300, rect[0])
        assertEquals(200, rect[1])
    }

    @Test fun draggingRightMovesTheWindowLeft() {
        // scale = viewSize * zoom / shortest = 400 * 2 / 800 = 1, so one screen pixel is one
        // bitmap pixel and the window follows the finger exactly.
        val rect = CropGeometry.rect(1000, 800, 2f, 100f, 0f, 400f)
        assertEquals(200, rect[0])
    }

    @Test fun theWindowNeverLeavesTheBitmap() {
        val rect = CropGeometry.rect(1000, 800, 4f, 100000f, -100000f, 400f)
        assertEquals(200, rect[2])
        assertTrue("left ${rect[0]}", rect[0] in 0..800)
        assertTrue("top ${rect[1]}", rect[1] in 0..600)
    }

    @Test fun offsetsAreLimitedToTheOverflow() {
        // A square picture can never be dragged at rest.
        assertEquals(0f, CropGeometry.maxOffsetX(800, 800, 1f, 400f), 0.001f)
        // 1000x800 at zoom 2 is 1000x800 on a 400px canvas: 300px spare each way, 200px up and down.
        assertEquals(300f, CropGeometry.maxOffsetX(1000, 800, 2f, 400f), 0.001f)
        assertEquals(200f, CropGeometry.maxOffsetY(1000, 800, 2f, 400f), 0.001f)
    }

    @Test fun zoomIsHeldBetweenTheLimits() {
        assertEquals(CropGeometry.MAX_ZOOM, CropGeometry.clampZoom(9f), 0.001f)
        assertEquals(CropGeometry.MIN_ZOOM, CropGeometry.clampZoom(0.2f), 0.001f)
        assertEquals(2.5f, CropGeometry.clampZoom(2.5f), 0.001f)
    }

    @Test fun anUnmeasuredCanvasStillReturnsAUsableSquare() {
        val rect = CropGeometry.rect(900, 400, 1f, 0f, 0f, 0f)
        assertEquals(250, rect[0])
        assertEquals(0, rect[1])
        assertEquals(400, rect[2])
    }
}
