package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphImageViewport
import kotlin.math.roundToInt

@Composable
internal fun MultiGlyphResultsPanel(
    bitmap: Bitmap,
    results: List<MultiGlyphItem>,
    showOverview: Boolean = true,
    selectedIndex: Int? = null,
    onRegionSelected: ((Int) -> Unit)? = null,
    onTryAnother: (() -> Unit)? = null,
    onOpenDictionary: (() -> Unit)? = null,
    onOpenSign: ((Int, String) -> Unit)? = null,
) {
    if (results.isEmpty()) return
    var localSelection by rememberSaveable(bitmap, results) { mutableIntStateOf(0) }
    val index = (selectedIndex ?: localSelection).coerceIn(0, results.lastIndex)
    val select: (Int) -> Unit = { next ->
        localSelection = next.coerceIn(0, results.lastIndex)
        onRegionSelected?.invoke(localSelection)
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Glyph by glyph", style = MaterialTheme.typography.titleLarge)
            if (showOverview) {
                GlyphRegionOverlay(bitmap, results, index, select)
            }
            GlyphRegionPicker(results, index, select)
            val item = results[index]
            HorizontalDivider()
            Text("Region ${item.number} · row ${item.region.row}, column ${item.region.column}",
                style = MaterialTheme.typography.labelLarge)
            val preview = remember(bitmap, item.region.crop) {
                val (x,y,w,h) = item.region.crop.pixels(bitmap.width,bitmap.height)
                val cropped = Bitmap.createBitmap(bitmap,x,y,w,h)
                val scale = minOf(1f,512f/maxOf(w,h))
                val resized = if (scale < 1f) Bitmap.createScaledBitmap(cropped,
                    maxOf(1,(w*scale).roundToInt()),maxOf(1,(h*scale).roundToInt()),true) else cropped
                if (resized !== cropped && cropped !== bitmap) cropped.recycle()
                resized
            }
            val showsComparison = item.englishMatches.firstOrNull()?.let {
                com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy.accepts(it.visualOverlap)
            } == true
            if (!showsComparison) Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
                Image(preview.asImageBitmap(), contentDescription = "Detected glyph region ${item.number}",
                    modifier = Modifier.fillMaxWidth().height(176.dp).padding(12.dp), contentScale = ContentScale.Fit)
            }
            if (item.englishMatches.isEmpty()) {
                Text("No readable glyph found", style = MaterialTheme.typography.titleLarge)
                Text(item.note.ifBlank {
                    "No readable reference comparison is available for this region. Try a clearer photo with separated glyphs."
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScanRecoveryActions(onTryAnother, onOpenDictionary)
            } else GlyphReferenceResults(item.englishMatches, onOpenDictionary = onOpenDictionary, onTryAnother = onTryAnother,
                photo = preview, photoDescription = "Detected glyph region ${item.number}", onOpenSign = onOpenSign)
        }
    }
}

/** Numbered controls also support keyboard and screen-reader selection. */
@Composable
internal fun GlyphRegionPicker(results: List<MultiGlyphItem>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    if (results.isEmpty()) return
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, results) {
        if (selectedIndex in results.indices) listState.animateScrollToItem(selectedIndex)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${results.size} regions · select a number", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(results, key = { _, item -> item.number }) { index, item ->
                FilterChip(selected = index == selectedIndex, onClick = { onSelect(index) },
                    modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).semantics {
                        contentDescription = "Show region ${item.number}"
                    }, label = { Text(item.number.toString()) },
                    leadingIcon = if (index == selectedIndex) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null)
            }
        }
    }
}

@Composable
internal fun GlyphRegionOverlay(
    bitmap: Bitmap,
    results: List<MultiGlyphItem>,
    selectedIndex: Int? = null,
    onRegionSelected: ((Int) -> Unit)? = null,
) {
    val image = bitmap.asImageBitmap()
    val choose by rememberUpdatedState(onRegionSelected)
    val touch = if (onRegionSelected != null && results.isNotEmpty()) Modifier.pointerInput(bitmap, results) {
        detectTapGestures { point ->
            if (size.width > 0 && size.height > 0) {
                GlyphImageViewport.fit(bitmap.width, bitmap.height, size.width.toFloat(), size.height.toFloat())
                    .regionAt(point.x, point.y, results.map { it.region.crop })?.let { choose?.invoke(it) }
            }
        }
    } else Modifier
    Canvas(modifier = Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF14231C))
        .then(touch).semantics {
            contentDescription = "Scan image with ${results.size} numbered glyph boxes"
            selectedIndex?.let { index -> results.getOrNull(index)?.let {
                stateDescription = "Region ${it.number} selected"
            } }
        }) {
        if (size.width <= 0 || size.height <= 0) return@Canvas
        val viewport = GlyphImageViewport.fit(bitmap.width, bitmap.height, size.width, size.height)
        val imageWidth = viewport.width
        val imageHeight = viewport.height
        val originX = viewport.left
        val originY = viewport.top
        drawImage(image = image, dstOffset = IntOffset(originX.roundToInt(), originY.roundToInt()),
            dstSize = IntSize(imageWidth.roundToInt().coerceAtLeast(1), imageHeight.roundToInt().coerceAtLeast(1)))
        val yellow = Color(0xFFFFCF58)
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 12.dp.toPx()
            isFakeBoldText = true
            setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
        }
        results.forEachIndexed { index, result ->
            val region = result.region.crop
            val l = originX + region.left * imageWidth
            val t = originY + region.top * imageHeight
            val w = (region.right - region.left) * imageWidth
            val h = (region.bottom - region.top) * imageHeight
            val active = index == selectedIndex
            if (active) drawRect(yellow.copy(alpha = .15f), Offset(l, t), Size(w, h))
            drawRect(Color.Black.copy(alpha = .8f), Offset(l, t), Size(w, h), style = Stroke(5.dp.toPx()))
            drawRect(if (active || selectedIndex == null) yellow else Color.White.copy(alpha = .75f),
                Offset(l, t), Size(w, h), style = Stroke((if (active) 3 else 2).dp.toPx()))
            drawRect(if (active) Color(0xFF245A49) else Color.Black.copy(alpha = .8f), Offset(l, t),
                Size(26.dp.toPx(), 20.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText(result.number.toString(), l + 5.dp.toPx(), t + 15.dp.toPx(), paint)
        }
    }
}
