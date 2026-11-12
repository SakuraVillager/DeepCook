package com.deepcook.app.core.designsystem

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The approved vector artwork, shared by cards, lists and recipe details. */
@Composable
internal fun FavoriteGlyph(selected: Boolean, label: String?, modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.primary
    val scale = with(density) { iconSize.toPx() } / 24f
    val paint = remember(scale, color) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = 10f * scale
            this.color = color.toArgb()
        }
    }
    val textBounds = remember(paint, label) {
        Rect().also { if (label != null) paint.getTextBounds(label, 0, label.length, it) }
    }
    val labelRight = if (label == null) 24f * scale else 22f * scale + paint.measureText(label) + 1.6f * scale
    val width = with(density) { maxOf(iconSize.toPx(), labelRight).toDp() }
    val star = remember {
        Path().apply {
            moveTo(12f, 2f)
            lineTo(14.81f, 8.63f)
            lineTo(22f, 9.24f)
            lineTo(16.54f, 13.97f)
            lineTo(18.18f, 21f)
            lineTo(12f, 17.27f)
            lineTo(5.82f, 21f)
            lineTo(7.46f, 13.97f)
            lineTo(2f, 9.24f)
            lineTo(9.19f, 8.63f)
            close()
        }
    }
    Canvas(
        modifier.width(width).height(iconSize).graphicsLayer {
            // Clear only this icon's layer; the rounded cutout reveals its actual backdrop.
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            if (selected) drawPath(star, color)
            drawPath(star, color, style = Stroke(width = 1.35f, join = StrokeJoin.Round))
        }
        if (label != null) {
            drawRoundRect(
                Color.Transparent,
                topLeft = Offset(21f * scale, .1f * scale),
                size = Size(
                    maxOf(12.7f * scale, labelRight - 21f * scale),
                    8f * scale,
                ),
                cornerRadius = CornerRadius(3.5f * scale),
                blendMode = BlendMode.Clear,
            )
            drawIntoCanvas { canvas ->
                // Align visible ink, including font bearing, with the rounded star's top edge.
                canvas.nativeCanvas.drawText(
                    label,
                    22f * scale - textBounds.left,
                    1.325f * scale - textBounds.top,
                    paint,
                )
            }
        }
    }
}
