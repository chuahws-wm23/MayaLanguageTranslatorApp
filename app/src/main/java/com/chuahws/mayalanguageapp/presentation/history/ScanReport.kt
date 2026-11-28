package com.chuahws.mayalanguageapp.presentation.history

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.chuahws.mayalanguageapp.domain.model.GlyphScanCandidate
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMeaningCatalogue
import com.chuahws.mayalanguageapp.integration.glyph.GlyphScanPreviewCodec
import com.chuahws.mayalanguageapp.presentation.components.GlyphScanThumbnail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date

private const val SCORE_NOTE = "Visual similarity measures resemblance to a reference drawing. Confirm proposed readings and meanings against the inscription context."
private const val SOURCE = "TWKM / University of Bonn · CC BY 4.0 reference drawings"
private const val CATALOGUE = "https://classicmayan.org/portal/signcatalog"

private fun TranslationRecord.reportRegions(): List<Pair<String, GlyphScanCandidate?>> {
    val candidates = GlyphDisplayPolicy.visible(glyphCandidates)
    if (glyphScanKind !in setOf("BLOCKS", "COMPONENTS") && glyphRegionCount <= 1) {
        return listOf("Glyph result" to candidates.firstOrNull())
    }
    val count = maxOf(glyphRegionCount, candidates.maxOfOrNull { it.regionIndex } ?: 0).coerceIn(1, 64)
    return (1..count).map { index -> "Region $index" to candidates.firstOrNull { it.regionIndex == index } }
}

private fun GlyphScanCandidate.reportText() = listOf(
    "Proposed reading: ${reading.ifBlank { "Unavailable" }}",
    "Proposed meaning: ${englishGloss.ifBlank { "Unavailable" }}",
    if (twkmSign > 0) "Catalogue: TWKM #$twkmSign" else "Catalogue ID unavailable",
    "Visual similarity: ${GlyphDisplayPolicy.percent(visualOverlap.toFloat())}",
).joinToString("\n")

internal fun reportScanDate(scan: TranslationRecord): String =
    if (scan.createdAtMillis > 0) DateFormat.getDateTimeInstance().format(Date(scan.createdAtMillis))
    else "Date unavailable"

internal fun writeScanReport(context: Context, scan: TranslationRecord, note: String, output: OutputStream) {
    writeReport(context, listOf(scan), null, emptyList(), note, output)
}

private fun writeReport(context: Context, scans: List<TranslationRecord>, activity: List<String>?, activityDays: List<ScanDay>, note: String, output: OutputStream) {
    val document = PdfDocument()
    val catalogue = runCatching { GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context) }.getOrNull()
    var pageNumber = 0
    var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
    var y = 45f
    val green = Color.rgb(21, 70, 56)
    val designPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun drawPageAccent() {
        designPaint.color = green
        page.canvas.drawRect(0f, 0f, 595f, 9f, designPaint)
    }
    drawPageAccent()
    val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    fun finishPage() {
        designPaint.color = Color.rgb(205, 218, 210)
        page.canvas.drawRect(40f, 792f, 555f, 793f, designPaint)
        page.canvas.drawText("Maya Translate  •  $pageNumber", 40f, 810f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; textSize = 10f })
        document.finishPage(page)
    }
    fun reserve(height: Float) {
        if (y + height <= 780f) return
        finishPage()
        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
        drawPageAccent()
        y = 45f
    }
    fun paragraph(value: String, size: Float = 12f, heading: Boolean = false) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = if (heading) green else Color.DKGRAY
            isFakeBoldText = heading
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, 515)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
        for (line in 0 until layout.lineCount) {
            val height = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
            reserve(height)
            val text = value.substring(layout.getLineStart(line), layout.getLineEnd(line)).trimEnd()
            page.canvas.drawText(text, 40f, y + layout.getLineBaseline(line) - layout.getLineTop(line), paint)
            y += height
        }
        y += 8
    }
    fun section(label: String) {
        reserve(46f)
        designPaint.color = Color.rgb(225, 236, 228)
        val band = RectF(40f, y, 555f, y + 29f)
        page.canvas.drawRoundRect(band, 6f, 6f, designPaint)
        page.canvas.drawText(label, 51f, y + 19f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = green
            textSize = 13f
            isFakeBoldText = true
        })
        y += 39f
    }
    fun image(bitmap: Bitmap, maxWidth: Float, maxHeight: Float) {
        try {
            val scale = minOf(maxWidth / bitmap.width, maxHeight / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            reserve(height)
            designPaint.color = Color.rgb(244, 247, 244)
            page.canvas.drawRect(40f, y, 40f + maxWidth, y + height, designPaint)
            val x = 40f + (maxWidth - width) / 2f
            page.canvas.drawBitmap(bitmap, null, RectF(x, y, x + width, y + height), imagePaint)
            y += height + 10
        } finally {
            bitmap.recycle()
        }
    }
    try {
        paragraph(if (activity != null) "Maya Glyph Activity Report" else "Maya Glyph Scan Report", 26f, true)
        if (activity != null) {
            section("Activity at a glance")
            paragraph("Generated ${DateFormat.getDateTimeInstance().format(Date())}")
            activity.forEach { paragraph(it) }
            if (activityDays.isNotEmpty()) {
                reserve(166f)
                section("Saved scans · last 7 days")
                val peak = activityDays.maxOf { it.total }.coerceAtLeast(1)
                val interval = 515f / activityDays.size
                val bottom = y + 66f
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 9f }
                activityDays.forEachIndexed { index, day ->
                    val x = 46f + index * interval
                    val topSuggested = bottom - 52f * day.suggested / peak
                    val topTotal = bottom - 52f * day.total / peak
                    designPaint.color = Color.rgb(140, 102, 48)
                    page.canvas.drawRect(x, topTotal, x + 24f, topSuggested, designPaint)
                    designPaint.color = green
                    page.canvas.drawRect(x, topSuggested, x + 24f, bottom, designPaint)
                    page.canvas.drawText(day.total.toString(), x, topTotal - 4f, labelPaint)
                    page.canvas.drawText(day.date, x, bottom + 13f, labelPaint)
                }
                y = bottom + 23f
                paragraph("Green: suggested reference    Ochre: no suggestion", 10f)
            }
        } else {
            paragraph("${scans.size} selected ${if (scans.size == 1) "scan" else "scans"}")
            paragraph(SCORE_NOTE)
            scans.forEachIndexed { index, scan ->
                reserve(110f)
                section(if (scans.size > 1) "Scan ${index + 1} of ${scans.size}" else "Scan details")
                val regions = scan.reportRegions()
                paragraph("Scanned ${reportScanDate(scan)}")
                paragraph(if (regions.first().first == "Glyph result") "Single-glyph scan" else "Multi-glyph scan")
                paragraph("${regions.count { it.second != null }} of ${regions.size} results have a suggested reference")
                paragraph("Saved scan preview", 15f, true)
                val preview = GlyphScanPreviewCodec.decode(scan.glyphImageBase64)
                if (preview == null) paragraph("Image unavailable for this saved scan.")
                else image(preview, 515f, 235f)
                regions.forEach { (label, candidate) ->
                    reserve(90f)
                    paragraph(label, 16f, true)
                    paragraph(candidate?.reportText() ?: "No match found in the saved results.")
                    val reference = candidate?.let { item -> catalogue?.references?.firstOrNull { it.twkmSign == item.twkmSign } }
                    if (reference != null && catalogue != null) {
                        reserve(115f)
                        paragraph("Catalogue example · ${reference.graph}")
                        image(catalogue.imageFor(reference), 96f, 96f)
                    }
                }
            }
        }
        if (note.isNotBlank()) {
            reserve(64f)
            section("Research note")
            paragraph(note.trim().take(2000))
        }
        if (activity == null) {
            reserve(85f)
            section("Reference source")
            paragraph(SOURCE)
            paragraph(CATALOGUE)
        }
        finishPage()
        document.writeTo(output)
    } finally {
        document.close()
    }
}

@Composable
internal fun ScanReportDialog(scan: TranslationRecord, onClose: () -> Unit) =
    ReportPreview(listOf(scan), onClose = onClose)

@Composable
internal fun ReportPreview(
    scans: List<TranslationRecord>,
    activity: List<String>? = null,
    activityDays: List<ScanDay> = emptyList(),
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scan = scans.singleOrNull()
    var note by rememberSaveable(scan?.translationId, activity != null) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val fileName = if (activity != null) "Maya-glyph-activity.pdf" else "Maya-glyph-scans.pdf"
    fun export(uri: Uri?) {
        val reportNote = note
        busy = true
        status = null
        scope.launch {
            try {
                val sharedUri = withContext(Dispatchers.IO) {
                    if (uri != null) {
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use {
                            writeReport(context, scans, activity, activityDays, reportNote, it)
                        }
                        null
                    } else {
                        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
                        val file = File(directory, "glyph-${java.util.UUID.randomUUID()}.pdf")
                        file.outputStream().use { writeReport(context, scans, activity, activityDays, reportNote, it) }
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file, fileName)
                    }
                }
                if (sharedUri != null) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, sharedUri)
                        clipData = android.content.ClipData.newRawUri("Glyph scan report", sharedUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Share scan report"))
                } else status = "PDF saved."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status = "Could not export the PDF. Please try again."
            } finally {
                busy = false
            }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) export(uri)
    }
    val summaries = remember(scans) { scans.map { it.reportRegions() } }
    val matched = summaries.sumOf { regions -> regions.count { it.second != null } }
    val unmatched = summaries.sumOf { it.size } - matched

    Dialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (activity != null) "Activity report preview" else "Scan report preview",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(onClick = onClose, enabled = !busy) { Text("Close") }
                }
                HorizontalDivider()
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (activity != null) {
                        item {
                            ReportActivityChart(activityDays)
                        }
                        itemsIndexed(activity) { _, metric ->
                            val label = metric.substringBefore(": ")
                            val value = metric.substringAfter(": ", "")
                            if (value.toIntOrNull() != null) {
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Text(value, style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else Text(metric, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        item {
                            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReportMetric(scans.size, "Scans", Modifier.weight(1f))
                                ReportMetric(matched, "Suggested matches", Modifier.weight(1f))
                                ReportMetric(unmatched, "Without match", Modifier.weight(1f))
                            }
                        }
                        itemsIndexed(scans, key = { _, record -> record.translationId }) { index, record ->
                            ScanPreviewCard(record, index + 1, summaries[index], initiallyExpanded = scans.size == 1)
                        }
                        item {
                            Text(SCORE_NOTE, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Text(SOURCE, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item {
                        OutlinedTextField(
                            note, { note = it.take(2000) }, enabled = !busy,
                            label = { Text("Research note (optional)") },
                            supportingText = {
                                Text(if (activity == null) "Add an object ID, photo source, or detail to verify."
                                else "Add context for this activity summary.")
                            },
                            minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { export(null) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text("Share PDF")
                        }
                        Button(onClick = { save.launch(fileName) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text("Save PDF")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetric(count: Int, label: String, modifier: Modifier) {
    Card(modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ScanPreviewCard(
    scan: TranslationRecord,
    number: Int,
    regions: List<Pair<String, GlyphScanCandidate?>>,
    initiallyExpanded: Boolean,
) {
    var expanded by rememberSaveable(scan.translationId) { mutableStateOf(initiallyExpanded) }
    val best = regions.firstNotNullOfOrNull { it.second }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphScanThumbnail(scan.glyphImageBase64, side = 72.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Scan $number · ${reportScanDate(scan)}", style = MaterialTheme.typography.labelMedium)
                    Text(best?.let { "${it.reading} · ${it.englishGloss}" } ?: "No match found",
                        style = MaterialTheme.typography.titleMedium, maxLines = 3)
                    Text("${regions.count { it.second != null }} of ${regions.size} regions with a suggestion",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide region results" else "View region results")
            }
            if (expanded) {
                regions.forEach { (label, candidate) ->
                    HorizontalDivider()
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(candidate?.reportText() ?: "No match found in the saved results.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
