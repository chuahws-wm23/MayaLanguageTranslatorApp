package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import kotlin.math.min
import kotlin.math.roundToInt

/** Drag a new rectangle; coordinates are relative to the fitted image, not its letterbox. */
@Composable
internal fun GlyphCropPreview(bitmap: Bitmap, crop: GlyphCrop, enabled: Boolean, onCrop: (GlyphCrop) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    fun imageBounds(): Rect {
        val scale = min(canvasSize.width.toFloat() / bitmap.width, canvasSize.height.toFloat() / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val x = (canvasSize.width - w) / 2
        val y = (canvasSize.height - h) / 2
        return Rect(x, y, x + w, y + h)
    }
    Canvas(
        modifier = Modifier.fillMaxWidth().height(320.dp).background(Color(0xFFEAEAEA))
            .semantics { contentDescription = "Selected glyph. Drag a rectangle around one glyph, or use Full image below." }
            .onSizeChanged { canvasSize = it }
            .pointerInput(bitmap, canvasSize, enabled) {
                if (!enabled) return@pointerInput
                var start: Offset? = null
                detectDragGestures(
                    onDragStart = { position ->
                        val bounds = imageBounds()
                        start = if (bounds.width > 0 && bounds.height > 0 && bounds.contains(position)) position else null
                    },
                    onDragEnd = { start = null },
                    onDragCancel = { start = null },
                    onDrag = { change, _ ->
                        val anchor = start
                        if (anchor != null) {
                            change.consume()
                            val b = imageBounds()
                            GlyphCrop.between((anchor.x - b.left) / b.width, (anchor.y - b.top) / b.height,
                                (change.position.x - b.left) / b.width, (change.position.y - b.top) / b.height)
                                ?.let(onCrop)
                        }
                    },
                )
            }
    ) {
        val b = imageBounds()
        if (b.width <= 0 || b.height <= 0) return@Canvas
        drawImage(image, dstOffset = IntOffset(b.left.roundToInt(), b.top.roundToInt()),
            dstSize = IntSize(b.width.roundToInt().coerceAtLeast(1), b.height.roundToInt().coerceAtLeast(1)))
        val l = b.left + crop.left * b.width
        val t = b.top + crop.top * b.height
        val r = b.left + crop.right * b.width
        val bottom = b.top + crop.bottom * b.height
        val shade = Color.Black.copy(alpha = .45f)
        drawRect(shade, Offset(b.left, b.top), Size(b.width, t - b.top))
        drawRect(shade, Offset(b.left, bottom), Size(b.width, b.bottom - bottom))
        drawRect(shade, Offset(b.left, t), Size(l - b.left, bottom - t))
        drawRect(shade, Offset(r, t), Size(b.right - r, bottom - t))
        drawRect(Color.White, Offset(l, t), Size(r - l, bottom - t), style = Stroke(3.dp.toPx()))
        drawRect(Color.Black, Offset(l, t), Size(r - l, bottom - t), style = Stroke(1.dp.toPx()))
    }
}
