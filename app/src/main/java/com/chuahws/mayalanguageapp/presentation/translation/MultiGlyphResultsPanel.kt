package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    showOverview: Boolean = true,
) {
    if (results.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Block reference comparisons", style = MaterialTheme.typography.titleLarge)
            if (showOverview) {
                GlyphRegionOverlay(bitmap, results)
                Text("${results.size} regions outlined · browse each result below",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            var selected by rememberSaveable(bitmap) { mutableIntStateOf(0) }
            val index=selected.coerceIn(0,results.lastIndex)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={selected=maxOf(0,index-1)},enabled=index>0) {
                    Icon(Icons.Rounded.ChevronLeft,contentDescription="Previous region")
                }
                Text("Region ${index+1} of ${results.size}",style=MaterialTheme.typography.titleMedium)
                IconButton(onClick={selected=minOf(results.lastIndex,index+1)},enabled=index<results.lastIndex) {
                    Icon(Icons.Rounded.ChevronRight,contentDescription="Next region")
                }
            }
            val item=results[index]
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
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
                Image(preview.asImageBitmap(), contentDescription = "Detected glyph region ${item.number}",
                    modifier = Modifier.fillMaxWidth().height(144.dp).background(Color.White), contentScale = ContentScale.Fit)
                if(item.englishMatches.isEmpty()) {
                    Text("No exact match found",style=MaterialTheme.typography.titleLarge)
                    Text("No readable reference comparison is available for this region. Try a clearer photo with separated glyphs.",
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else GlyphReferenceResults(item.englishMatches)

            }
        }
    }
}

@Composable
internal fun GlyphRegionOverlay(bitmap: Bitmap, results: List<MultiGlyphItem>) {
    val image = bitmap.asImageBitmap()
    Canvas(modifier = Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF14231C))
        .semantics { contentDescription = "Scan image with ${results.size} numbered glyph boxes" }) {
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
            drawRect(Color.Black.copy(alpha = .8f), Offset(l, t), Size(w, h), style = Stroke(5.dp.toPx()))
            drawRect(yellow, Offset(l, t), Size(w, h), style = Stroke(3.dp.toPx()))
            drawRect(Color.Black.copy(alpha = .72f), Offset(l, t),
                Size(23.dp.toPx(), 17.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText(
                result.number.toString(), l + 4.dp.toPx(), t + 13.dp.toPx(), paint,
            )
        }
    }
}
