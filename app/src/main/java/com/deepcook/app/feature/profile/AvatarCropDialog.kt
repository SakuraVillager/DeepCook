package com.deepcook.app.feature.profile

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-screen cropper for the avatar.
 *
 * The circle always shows exactly the square that will be stored: the canvas draws the computed
 * bitmap square into the circle instead of scaling the whole picture and trusting two transforms
 * to agree, so what the user frames is what [AvatarStore] receives.
 */
@Composable
fun AvatarCropDialog(source: Bitmap, onCancel: () -> Unit, onConfirm: (Bitmap) -> Unit) {
    val image = remember(source) { source.asImageBitmap() }
    val width = source.width
    val height = source.height
    var zoom by remember { mutableFloatStateOf(CropGeometry.MIN_ZOOM) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableFloatStateOf(0f) }
    val rect = CropGeometry.rect(width, height, zoom, offset.x, offset.y, viewSize)

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("调整头像", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "双指缩放，拖动选择要显示的部分。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    CropCircle(
                        image = image,
                        rect = rect,
                        side = side,
                        onSize = { viewSize = it },
                        onTransform = { zoomChange, pan ->
                            val next = CropGeometry.clampZoom(zoom * zoomChange)
                            val maxX = CropGeometry.maxOffsetX(width, height, next, viewSize)
                            val maxY = CropGeometry.maxOffsetY(width, height, next, viewSize)
                            zoom = next
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                        },
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(
                        onClick = {
                            onConfirm(Bitmap.createBitmap(source, rect[0], rect[1], rect[2], rect[2]))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = viewSize > 0f,
                    ) { Text("确定") }
                }
            }
        }
    }
}

@Composable
private fun CropCircle(
    image: ImageBitmap,
    rect: IntArray,
    side: Dp,
    onSize: (Float) -> Unit,
    onTransform: (zoomChange: Float, pan: Offset) -> Unit,
) {
    val transform by rememberUpdatedState(onTransform)
    val outline = MaterialTheme.colorScheme.outline
    val backdrop = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        Modifier
            .size(side)
            .clip(CircleShape)
            .background(backdrop)
            .onSizeChanged { onSize(it.width.toFloat()) }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ -> transform(zoom, pan) }
            },
    ) {
        drawImage(
            image = image,
            srcOffset = IntOffset(rect[0], rect[1]),
            srcSize = IntSize(rect[2], rect[2]),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.High,
        )
        drawCircle(
            color = outline,
            radius = size.minDimension / 2f - 1.dp.toPx() / 2f,
            style = Stroke(1.dp.toPx()),
        )
    }
}
