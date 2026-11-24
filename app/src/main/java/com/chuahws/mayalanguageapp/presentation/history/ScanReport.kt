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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    writeReport(context, listOf(scan), null, note, output)
}

private fun writeReport(context: Context, scans: List<TranslationRecord>, activity: List<String>?, note: String, output: OutputStream) {
    val document = PdfDocument()
    val catalogue = runCatching { GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context) }.getOrNull()
    var pageNumber = 0
    var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
    var y = 40f
    val green = Color.rgb(21, 70, 56)
    val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    fun finishPage() {
        page.canvas.drawText("Maya Translate  •  $pageNumber", 40f, 810f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; textSize = 10f })
        document.finishPage(page)
    }
    fun reserve(height: Float) {
        if (y + height <= 780f) return
        finishPage()
        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
        y = 40f
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
    fun image(bitmap: Bitmap, maxWidth: Float, maxHeight: Float) {
        try {
            val scale = minOf(maxWidth / bitmap.width, maxHeight / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            reserve(height)
            page.canvas.drawBitmap(bitmap, null, RectF(40f, y, 40f + width, y + height), imagePaint)
            y += height + 10
        } finally {
            bitmap.recycle()
        }
    }
    try {
        paragraph(if (activity != null) "Maya Glyph Activity Report" else "Maya Glyph Scan Report", 26f, true)
        if (activity != null) {
            paragraph("Generated ${DateFormat.getDateTimeInstance().format(Date())}")
            activity.forEach { paragraph(it) }
        } else {
            paragraph("${scans.size} selected ${if (scans.size == 1) "scan" else "scans"}")
            paragraph(SCORE_NOTE)
            scans.forEachIndexed { index, scan ->
                if (scans.size > 1) {
                    reserve(80f)
                    paragraph("Scan ${index + 1}", 18f, true)
                }
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
            reserve(50f)
            paragraph("Research note", 16f, true)
            paragraph(note.trim().take(2000))
        }
        if (activity == null) {
            reserve(75f)
            paragraph("Reference source", 16f, true)
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
                            writeReport(context, scans, activity, reportNote, it)
                        }
                        null
                    } else {
                        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
                        val file = File(directory, "glyph-${java.util.UUID.randomUUID()}.pdf")
                        file.outputStream().use { writeReport(context, scans, activity, reportNote, it) }
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
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (activity != null) "Activity report preview" else "Scan report preview") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (activity != null) {
                    activity.forEach { Text(it) }
                } else if (scan != null) {
                    Text(reportScanDate(scan))
                    GlyphScanThumbnail(scan.glyphImageBase64, side = 112.dp)
                    Text("Saved scan preview")
                    scan.reportRegions().forEach { (label, candidate) ->
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text(candidate?.reportText() ?: "No match found in the saved results.")
                    }
                    Text(SCORE_NOTE, style = MaterialTheme.typography.bodySmall)
                    Text(SOURCE, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("${scans.size} scans · includes saved images and results by region")
                    scans.forEach { Text(reportScanDate(it)) }
                    Text(SCORE_NOTE, style = MaterialTheme.typography.bodySmall)
                }
                if (activity == null) Text("Record the site, object ID, photo source, or what you want to verify.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(note, { note = it.take(2000) }, enabled = !busy,
                    label = { Text("Research note (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                status?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = { save.launch(fileName) }, enabled = !busy) { Text("Save PDF") } },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !busy) { Text("Close") }
            TextButton(onClick = { export(null) }, enabled = !busy) { Text("Share PDF") }
        },
    )
}
