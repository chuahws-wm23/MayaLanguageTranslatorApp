package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMeaningCatalogue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun MultiGlyphResultsPanel(
    bitmap: Bitmap,
    results: List<MultiGlyphItem>,
    onInspect: (MultiGlyphItem) -> Unit = {},
) {
    if (results.isEmpty()) return
    val context = LocalContext.current
    val catalogue = remember(context) {
        runCatching { GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context) }.getOrNull()
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Block reference comparisons", style = MaterialTheme.typography.titleLarge)
            MultiGlyphNumberedPreview(bitmap, results)
            Text("Numbered scan photo for reference", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            results.forEach { item ->
                HorizontalDivider()
                Text("Region ${item.number} · row ${item.region.row}, column ${item.region.column}",
                    style = MaterialTheme.typography.labelLarge)
                val preview = remember(bitmap, item.region.crop) {
                    val (x,y,w,h) = item.region.crop.pixels(bitmap.width,bitmap.height)
                    val cropped = Bitmap.createBitmap(bitmap,x,y,w,h)
                    val scale = minOf(1f,128f/maxOf(w,h))
                    val resized = if(scale<1f) Bitmap.createScaledBitmap(cropped,
                        maxOf(1,(w*scale).roundToInt()),maxOf(1,(h*scale).roundToInt()),true) else cropped
                    if(resized !== cropped && cropped !== bitmap)cropped.recycle()
                    resized
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(preview.asImageBitmap(), contentDescription = "Uploaded crop for region ${item.number}",
                        modifier = Modifier.size(112.dp).background(Color.White), contentScale = ContentScale.Fit)
                    TextButton(onClick = { onInspect(item) }) { Text("Inspect / correct crop") }
                }
                Text(item.note, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val meaning = item.englishMatches.firstOrNull()?.reference
                val sound = item.visualMatches.firstOrNull()?.reference?.syllable
                when {
                    meaning != null -> {
                        Text("Closest visual match · ${meaning.english}",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Maya reading · ${meaning.reading}")
                        Text("Visual alternative · unconfirmed", style = MaterialTheme.typography.bodySmall)
                        var expanded by remember(item.number, item.englishMatches) { mutableStateOf(false) }
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Close references" else "Compare ${item.englishMatches.size} reference drawings")
                        }
                        if (expanded && catalogue != null) {
                            item.englishMatches.forEachIndexed { index, match ->
                                EnglishMeaningMatchRow(catalogue, match, index + 1)
                            }
                        }
                    }
                    item.reading != null -> Text("Possible reading · ${item.reading} — ${item.englishGloss}")
                    sound != null -> Text("Nearest syllable reference · $sound (unconfirmed).")
                    else -> Text("No reference comparison for this region. A block may contain " +
                        "several signs, so try a closer photo of one glyph.")
                }
            }
        }
    }
}

@Composable
private fun MultiGlyphNumberedPreview(bitmap: Bitmap, results: List<MultiGlyphItem>) {
    val image = bitmap.asImageBitmap()
    Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
        val scale = min(size.width / bitmap.width, size.height / bitmap.height)
        val imageWidth = bitmap.width * scale
        val imageHeight = bitmap.height * scale
        val originX = (size.width - imageWidth) / 2
        val originY = (size.height - imageHeight) / 2
        drawImage(
            image = image,
            dstOffset = IntOffset(originX.roundToInt(), originY.roundToInt()),
            dstSize = IntSize(imageWidth.roundToInt().coerceAtLeast(1),
                imageHeight.roundToInt().coerceAtLeast(1)),
        )
        val yellow = Color(0xFFFFCF58)
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 12.dp.toPx()
            isFakeBoldText = true
            setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
        }
        results.forEach { result ->
            val region = result.region.crop
            val l = originX + region.left * imageWidth
            val t = originY + region.top * imageHeight
            val w = (region.right - region.left) * imageWidth
            val h = (region.bottom - region.top) * imageHeight
            if (w <= 0 || h <= 0) return@forEach
            drawRect(yellow, Offset(l, t), Size(w, h), style = Stroke(2.dp.toPx()))
            drawRect(Color.Black.copy(alpha = .72f), Offset(l, t),
                Size(23.dp.toPx(), 17.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText(
                result.number.toString(), l + 4.dp.toPx(), t + 13.dp.toPx(), paint,
            )
        }
    }
}
