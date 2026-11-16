package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualCatalogue
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun MultiGlyphResultsPanel(
    bitmap: Bitmap,
    results: List<MultiGlyphItem>,
    assisted: Boolean = false,
    individualComponents: Boolean = false,
    detectorNote: String? = null,
    onInspect: (GlyphCrop) -> Unit,
) {
    if (results.isEmpty()) return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val catalogue = remember(context) {
        runCatching { GlyphVisualCatalogue.fromAssetsOrNull(context) }.getOrNull()
    }
    val recognized = results.count { it.reading != null }
    val visuallyMatched = results.count { it.visualMatches.isNotEmpty() }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (individualComponents) "Individual glyph components (research)" else "Multiple glyph blocks detected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                results.size.toString() + " candidate regions found; " +
                    recognized + " have experimental readings and " +
                    visuallyMatched + " have visual image suggestions.",
                style = MaterialTheme.typography.bodyMedium,
            )
            detectorNote?.let { note ->
                Text(note, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (individualComponents)
                    "Yellow rectangles enclose individual graphic component masks predicted by v11. " +
                        "No sign identity, syllable, meaning, or real-photo recognition accuracy is verified. " +
                        "Tap Inspect to review the detected region."
                else if (assisted)
                    "Yellow boxes are an APPROXIMATE user-chosen grid, not detected glyph boundaries. " +
                        "Check each region and adjust crop/row/column counts if misaligned. " +
                        "Each block may contain multiple signs; no whole-inscription translation."
                else
                    "Yellow boxes mark automatically separated BLOCKS. A block may contain multiple " +
                        "signs. These are tentative visual matches, NOT a translation of the inscription. " +
                        "Region order is for display only, not verified Maya reading order.",
                style = MaterialTheme.typography.bodySmall,
            )
            MultiGlyphNumberedPreview(bitmap, results)
            FilledTonalButton(
                onClick = {
                    clipboard.setText(AnnotatedString(
                        results.joinToString("\n") { item ->
                            "Region " + item.number + " (row " + item.region.row +
                                ", column " + item.region.column + "): " +
                                (if (item.reading != null)
                                    item.reading + " — " + item.englishGloss +
                                        " (UNVERIFIED visual guess)"
                                else if (item.visualMatches.isNotEmpty())
                                    "Unverified sounds: " +
                                        item.visualMatches.joinToString(", ") { it.reference.syllable }
                                else if (individualComponents) "Unidentified graphic component"
                                else "No reliable supported match")
                        } + "\nExperimental sign suggestions only; not an inscription translation."
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Copy unverified region list")
            }
            results.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Region " + item.number + " · Row " + item.region.row +
                                    ", Column " + item.region.column,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onInspect(item.region.crop) }) {
                                Text("Inspect")
                            }
                        }
                        Text(
                            if (item.reading != null)
                                "Possible: " + item.reading + " — " + item.englishGloss
                            else if (individualComponents) "Graphic component — not identified" else "Not identified",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (catalogue != null && item.visualMatches.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item.visualMatches.take(3).forEach { match ->
                                    val reference = remember(catalogue,match.reference.offset) {
                                        catalogue.referenceBitmap(match.reference)
                                    }
                                    Column(Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Image(
                                            bitmap=reference.asImageBitmap(),
                                            contentDescription="Visually similar ${match.reference.syllable}",
                                            modifier=Modifier.size(60.dp).background(Color.White),
                                        )
                                        Text(match.reference.syllable,
                                            style=MaterialTheme.typography.labelMedium)
                                        Text("${"%.0f".format(match.visualSimilarity*100)}% shape",
                                            style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiGlyphNumberedPreview(bitmap: Bitmap, results: List<MultiGlyphItem>) {
    val image = bitmap.asImageBitmap()
    Canvas(modifier = Modifier.fillMaxWidth().height(340.dp)) {
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
