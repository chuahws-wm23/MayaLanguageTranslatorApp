package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun MultiGlyphResultsPanel(
    bitmap: Bitmap,
    results: List<MultiGlyphItem>,
    onInspect: (GlyphCrop) -> Unit,
) {
    if (results.isEmpty()) return
    val clipboard = LocalClipboardManager.current
    val recognized = results.count { it.reading != null }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Multiple glyph blocks detected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                results.size.toString() + " candidate regions found; " +
                    recognized + " have model suggestions.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
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
                            else "Not identified",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
