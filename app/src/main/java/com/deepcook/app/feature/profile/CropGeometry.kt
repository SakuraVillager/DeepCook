package com.deepcook.app.feature.profile

import kotlin.math.roundToInt

/**
 * The maths behind the circular avatar cropper, kept out of the composable so it can be tested on
 * the JVM.
 *
 * The crop square is [viewSize] pixels wide on screen. The bitmap is scaled so that its shorter
 * side exactly fills that square at [MIN_ZOOM]; [offsetX]/[offsetY] are how far the picture has
 * been dragged, in screen pixels, and zooming in keeps the picture centred on the same spot. The
 * result is the square of bitmap pixels that is visible inside the circle.
 */
object CropGeometry {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 5f

    fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** Screen pixels per bitmap pixel at [zoom]. */
    private fun scale(bitmapWidth: Int, bitmapHeight: Int, zoom: Float, viewSize: Float): Float =
        viewSize * clampZoom(zoom) / minOf(bitmapWidth, bitmapHeight)

    /** How far the picture may be dragged horizontally before a blank edge shows in the circle. */
    fun maxOffsetX(bitmapWidth: Int, bitmapHeight: Int, zoom: Float, viewSize: Float): Float {
        if (viewSize <= 0f) return 0f
        val shown = bitmapWidth * scale(bitmapWidth, bitmapHeight, zoom, viewSize)
        return ((shown - viewSize) / 2f).coerceAtLeast(0f)
    }

    /** Same as [maxOffsetX] for the vertical axis. */
    fun maxOffsetY(bitmapWidth: Int, bitmapHeight: Int, zoom: Float, viewSize: Float): Float {
        if (viewSize <= 0f) return 0f
        val shown = bitmapHeight * scale(bitmapWidth, bitmapHeight, zoom, viewSize)
        return ((shown - viewSize) / 2f).coerceAtLeast(0f)
    }

    /** @return `[left, top, side]` in bitmap pixels, always wholly inside the bitmap. */
    fun rect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        viewSize: Float,
    ): IntArray {
        val shortest = minOf(bitmapWidth, bitmapHeight)
        if (viewSize <= 0f) return intArrayOf((bitmapWidth - shortest) / 2, (bitmapHeight - shortest) / 2, shortest)
        val pixels = scale(bitmapWidth, bitmapHeight, zoom, viewSize)
        val side = (shortest / clampZoom(zoom)).roundToInt().coerceIn(1, shortest)
        val left = (bitmapWidth / 2f - side / 2f - offsetX / pixels).roundToInt()
        val top = (bitmapHeight / 2f - side / 2f - offsetY / pixels).roundToInt()
        return intArrayOf(
            left.coerceIn(0, bitmapWidth - side),
            top.coerceIn(0, bitmapHeight - side),
            side,
        )
    }
}
