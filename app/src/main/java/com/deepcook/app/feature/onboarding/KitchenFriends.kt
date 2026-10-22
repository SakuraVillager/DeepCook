package com.deepcook.app.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/** Small code-native kitchen illustrations; no remote assets or oversized hero panels. */
@Composable
internal fun KitchenFriends(page: Int, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.primary
    val bowl = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier) {
        withTransform({ scale(size.width / 120f, size.height / 112f, pivot = Offset.Zero) }) {
            val pale = bowl.copy(alpha = .38f)
            drawCircle(pale, 46f, Offset(61f, 59f))
            // A tomato friend, a leaf and a little twinkle.
            val tomato = if (page == 1) Color(0xFFD9B971) else Color(0xFFF1A393)
            drawCircle(tomato, 17f, Offset(91f, 35f))
            val leaf = Path().apply {
                moveTo(90f, 22f)
                quadraticTo(76f, 10f, 79f, 24f)
                quadraticTo(86f, 29f, 91f, 24f)
                quadraticTo(105f, 17f, 103f, 26f)
                quadraticTo(98f, 30f, 91f, 24f)
            }
            drawPath(leaf, Color(0xFF819D6A))
            drawCircle(outline, 1.3f, Offset(86f, 35f))
            drawCircle(outline, 1.3f, Offset(96f, 35f))
            drawArc(
                outline,
                15f,
                150f,
                false,
                Offset(88f, 37f),
                Size(6f, 4f),
                style = Stroke(1.2f, cap = StrokeCap.Round),
            )
            // Rice with a rounded bowl and rosy cheeks.
            drawCircle(Color(0xFFFFF9E8), 22f, Offset(51f, 53f))
            drawCircle(Color(0xFFFFF9E8), 16f, Offset(32f, 58f))
            drawCircle(Color(0xFFFFF9E8), 16f, Offset(71f, 58f))
            val dish = Path().apply {
                moveTo(20f, 63f)
                lineTo(84f, 63f)
                cubicTo(81f, 87f, 70f, 96f, 52f, 96f)
                cubicTo(34f, 96f, 23f, 85f, 20f, 63f)
                close()
            }
            drawPath(dish, bowl)
            drawPath(dish, outline, style = Stroke(2.2f, cap = StrokeCap.Round))
            drawLine(outline, Offset(44f, 100f), Offset(61f, 100f), 3f, StrokeCap.Round)
            drawCircle(outline, 1.8f, Offset(42f, 76f))
            drawCircle(outline, 1.8f, Offset(62f, 76f))
            drawOval(Color(0xFFE8A391).copy(alpha = .65f), Offset(30f, 79f), Size(10f, 5f))
            drawOval(Color(0xFFE8A391).copy(alpha = .65f), Offset(64f, 79f), Size(10f, 5f))
            drawArc(
                outline,
                10f,
                160f,
                false,
                Offset(47f, 77f),
                Size(10f, 7f),
                style = Stroke(1.8f, cap = StrokeCap.Round),
            )
            if (page == 2) {
                drawRoundRect(Color(0xFF9CB587), Offset(8f, 27f), Size(25f, 24f), CornerRadius(7f))
                drawLine(outline, Offset(20f, 32f), Offset(20f, 40f), 1.6f, StrokeCap.Round)
                drawLine(outline, Offset(20f, 40f), Offset(25f, 43f), 1.6f, StrokeCap.Round)
            } else if (page == 3) {
                drawLine(Color(0xFF9CB587), Offset(11f, 39f), Offset(16f, 44f), 3f, StrokeCap.Round)
                drawLine(Color(0xFF9CB587), Offset(16f, 44f), Offset(26f, 31f), 3f, StrokeCap.Round)
            } else {
                drawLine(outline.copy(alpha = .5f), Offset(18f, 33f), Offset(28f, 33f), 1.6f, StrokeCap.Round)
                drawLine(outline.copy(alpha = .5f), Offset(23f, 28f), Offset(23f, 38f), 1.6f, StrokeCap.Round)
            }
            drawCircle(Color(0xFF9CB587), 2.5f, Offset(101f, 84f))
        }
    }
}
