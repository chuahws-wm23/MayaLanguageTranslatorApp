package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphImageViewport
import kotlin.math.roundToInt

/**
 * User-editable proposed regions. Geometry is normalised to the source image;
 * edits never imply a verified glyph identification.
 */
internal object GlyphRegionGeometry {
    private const val MIN_SIDE = .06f

    fun move(region: GlyphCrop, dx: Float, dy: Float): GlyphCrop {
        val width = region.right - region.left
        val height = region.bottom - region.top
        val left = (region.left + dx).coerceIn(0f, 1f - width)
        val top = (region.top + dy).coerceIn(0f, 1f - height)
        return GlyphCrop(left, top, left + width, top + height)
    }

    fun resize(region: GlyphCrop, dx: Float, dy: Float): GlyphCrop =
        GlyphCrop(
            region.left,
            region.top,
            (region.right + dx).coerceIn(region.left + MIN_SIDE, 1f),
            (region.bottom + dy).coerceIn(region.top + MIN_SIDE, 1f),
        )

    fun newRegion(existing: List<GlyphCrop>): GlyphCrop {
        val shift = (existing.size % 5) * .08f
        return GlyphCrop(.22f + shift, .22f + shift, .58f + shift, .58f + shift)
    }
}

@Composable
internal fun GlyphRegionEditor(
    bitmap: Bitmap,
    regions: List<GlyphCrop>,
    selectedIndex: Int,
    singleMode: Boolean,
    onSelect: (Int) -> Unit,
    onRegionsChanged: (List<GlyphCrop>) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    val latestRegions by rememberUpdatedState(regions)
    val latestChanged by rememberUpdatedState(onRegionsChanged)
    val latestSelect by rememberUpdatedState(onSelect)
    val selected = selectedIndex.coerceIn(0, (regions.size - 1).coerceAtLeast(0))
    val preview = remember(bitmap) { bitmap.asImageBitmap() }

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (singleMode) "Select one complete glyph" else "Adjust detected glyph areas",
                style = MaterialTheme.typography.titleLarge)
            Text("Drag inside a box to move it, or drag its lower-right handle to resize. " +
                "Only the chosen areas will be compared with reference drawings.",
                style = MaterialTheme.typography.bodySmall)

            Canvas(
                Modifier.fillMaxWidth().height(285.dp).background(Color(0xFF1D2A24))
                    .pointerInput(bitmap) {
                        var moving = -1
                        var resizing = false
                        var working = emptyList<GlyphCrop>()
                        detectDragGestures(
                            onDragStart = { start ->
                                working = latestRegions
                                val viewport = GlyphImageViewport.fit(bitmap.width, bitmap.height,
                                    size.width.toFloat(), size.height.toFloat())
                                moving = viewport.regionAt(start.x, start.y, working) ?: -1
                                if (moving >= 0) {
                                    latestSelect(moving)
                                    val box = working[moving]
                                    val handle = Offset(
                                        viewport.left + box.right * viewport.width,
                                        viewport.top + box.bottom * viewport.height,
                                    )
                                    resizing = (handle - start).getDistance() <= 34.dp.toPx()
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                if (moving !in working.indices) return@detectDragGestures
                                val viewport = GlyphImageViewport.fit(bitmap.width, bitmap.height,
                                    size.width.toFloat(), size.height.toFloat())
                                val current = working[moving]
                                val updated = if (resizing) GlyphRegionGeometry.resize(
                                    current, amount.x / viewport.width, amount.y / viewport.height,
                                ) else GlyphRegionGeometry.move(
                                    current, amount.x / viewport.width, amount.y / viewport.height,
                                )
                                working = working.toMutableList().also { it[moving] = updated }
                                latestChanged(working)
                            },
                            onDragEnd = { moving = -1 },
                            onDragCancel = { moving = -1 },
                        )
                    }
                    .pointerInput(bitmap) {
                        detectTapGestures { tap ->
                            val viewport = GlyphImageViewport.fit(bitmap.width, bitmap.height,
                                size.width.toFloat(), size.height.toFloat())
                            viewport.regionAt(tap.x, tap.y, latestRegions)?.let(latestSelect)
                        }
                    },
            ) {
                val viewport = GlyphImageViewport.fit(bitmap.width, bitmap.height, size.width, size.height)
                drawImage(preview,
                    dstOffset = IntOffset(viewport.left.roundToInt(), viewport.top.roundToInt()),
                    dstSize = IntSize(viewport.width.roundToInt().coerceAtLeast(1),
                        viewport.height.roundToInt().coerceAtLeast(1)))
                val marker = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 14.sp.toPx()
                    isFakeBoldText = true
                }
                regions.forEachIndexed { index, box ->
                    val left = viewport.left + box.left * viewport.width
                    val top = viewport.top + box.top * viewport.height
                    val width = (box.right - box.left) * viewport.width
                    val height = (box.bottom - box.top) * viewport.height
                    val active = index == selected
                    val color = if (active) Color(0xFFFFD166) else Color(0xFF70D6BB)
                    drawRect(color.copy(alpha = if (active) .14f else .06f), Offset(left, top), Size(width, height))
                    drawRect(color, Offset(left, top), Size(width, height),
                        style = Stroke(if (active) 3.dp.toPx() else 2.dp.toPx()))
                    drawCircle(color, radius = if (active) 8.dp.toPx() else 5.dp.toPx(),
                        center = Offset(left + width, top + height))
                    drawRect(Color(0xFF19362C), Offset(left, top), Size(25.dp.toPx(), 22.dp.toPx()))
                    drawContext.canvas.nativeCanvas.drawText((index + 1).toString(),
                        left + 6.dp.toPx(), top + 16.dp.toPx(), marker)
                }
            }

            Text(if (regions.isEmpty()) "No areas selected. Add one to continue."
                else "Area ${selected + 1} of ${regions.size} · boxes are editable",
                style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val updated = regions + GlyphRegionGeometry.newRegion(regions)
                        onRegionsChanged(updated)
                        onSelect(updated.lastIndex)
                    },
                    enabled = !singleMode && regions.size < 48,
                    modifier = Modifier.weight(1f),
                ) { Text("Add area") }
                OutlinedButton(
                    onClick = {
                        val updated = regions.filterIndexed { index, _ -> index != selected }
                        onRegionsChanged(updated)
                        onSelect((selected - 1).coerceAtLeast(0))
                    },
                    enabled = !singleMode && regions.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Remove area") }
            }
            if (regions.isNotEmpty()) {
                Text("Fine adjustment", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("←" to (-.015f to 0f), "↑" to (0f to -.015f),
                        "↓" to (0f to .015f), "→" to (.015f to 0f)).forEach { (title, delta) ->
                        OutlinedButton(onClick = {
                            onRegionsChanged(regions.toMutableList().also {
                                it[selected] = GlyphRegionGeometry.move(it[selected], delta.first, delta.second)
                            })
                        }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                            Text(title)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        onRegionsChanged(regions.toMutableList().also {
                            it[selected] = GlyphRegionGeometry.resize(it[selected], -.025f, -.025f)
                        })
                    }, modifier = Modifier.weight(1f)) { Text("Smaller") }
                    OutlinedButton(onClick = {
                        onRegionsChanged(regions.toMutableList().also {
                            it[selected] = GlyphRegionGeometry.resize(it[selected], .025f, .025f)
                        })
                    }, modifier = Modifier.weight(1f)) { Text("Larger") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = onApply, enabled = regions.isNotEmpty(),
                    modifier = Modifier.weight(1f)) { Text("Scan these areas") }
            }
            Text("Visual matches are suggestions, not confirmed historical translations.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
