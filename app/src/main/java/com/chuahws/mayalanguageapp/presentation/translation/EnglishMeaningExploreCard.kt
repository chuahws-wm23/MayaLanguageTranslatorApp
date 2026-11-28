package com.chuahws.mayalanguageapp.presentation.translation

import com.chuahws.mayalanguageapp.presentation.components.ReferenceDrawing
import com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy
import android.graphics.Bitmap
import com.chuahws.mayalanguageapp.presentation.dictionary.GlyphTable
import com.chuahws.mayalanguageapp.presentation.dictionary.GlyphTableRow
import com.chuahws.mayalanguageapp.domain.service.ListPaging
import com.chuahws.mayalanguageapp.presentation.components.ListPaginationControls
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphPanelGuard
import com.chuahws.mayalanguageapp.integration.glyph.GlyphImageQuality
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphScanPreviewCodec
import com.chuahws.mayalanguageapp.domain.model.GlyphScanCapture
import com.chuahws.mayalanguageapp.domain.model.GlyphScanCandidate
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMeaningCatalogue
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishReference
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMatch
import com.chuahws.mayalanguageapp.integration.glyph.GlyphLearningNotes
import com.chuahws.mayalanguageapp.integration.glyph.GlyphMoreLearningNotes
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualCatalogue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Additional OFFLINE English meaning reference search, not another trained
 * classification head. Its nearest visual match can be incorrect even when the
 * scholarly logogram reading itself has a strong evidence rating.
 */
internal const val MAX_CLOSEST_REFERENCES = 1

@Composable
internal fun EnglishMeaningExploreCard(
    bitmap: Bitmap?,
    crop: GlyphCrop,
    busy: Boolean,
    browseOnly: Boolean = false,
    matchOnly: Boolean = false,
    onSearchCompleted: (GlyphScanCapture) -> Unit = {},
) {
    require(!browseOnly || !matchOnly)
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val glossary = remember(context) {
        runCatching { GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context) }.getOrNull()
    }
    val phonograms = remember(context) {
        runCatching { GlyphVisualCatalogue.fromAssetsOrNull(context) }.getOrNull()
    }
    var searchText by remember { mutableStateOf("") }
    var browse by remember(browseOnly) { mutableStateOf(browseOnly) }
    var page by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<List<GlyphEnglishMatch>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var qualityHints by remember { mutableStateOf<List<String>>(emptyList()) }
    var requestVersion by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(bitmap, crop) {
        onDispose { searchJob?.cancel() }
    }
    LaunchedEffect(bitmap,crop) {
        requestVersion++
        result = null
        qualityHints = emptyList()
        error = null
        working = false
    }

    Card(Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if (browseOnly) "English meanings" else "Reference matches",
                style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            if(glossary==null || phonograms==null) {
                Text("The expanded word-sign catalogue is not in this build yet. " +
                    "Update the fyp-development branch and rebuild the app.",
                    style=MaterialTheme.typography.bodySmall)
            } else {
                if (browseOnly) Text("Search by English meaning or Maya reading.")
                if (!browseOnly && bitmap != null) {
                    Button(
                        onClick = {
                            val original=bitmap
                            val chosen=crop
                            error=null;result=null;working=true
                            val request=requestVersion
                            searchJob?.cancel()
                            searchJob = scope.launch {
                                try {
                                    val (matches,preview,hints)=withContext(Dispatchers.Default) {
                                        val (x,y,w,h)=chosen.pixels(original.width,original.height)
                                        val isolated=Bitmap.createBitmap(original,x,y,w,h)
                                        val comparison = try {
                                            require(chosen != GlyphCrop() || !GlyphPanelGuard.looksLikeDensePanel(isolated)) {
                                                "This looks like a panel of signs. Choose Multiple glyphs, or crop one complete sign."
                                            }
                                            val hints = GlyphImageQuality.hints(isolated)
                                            glossary.search(isolated,phonograms,5) to hints
                                        } finally {
                                            if(isolated!==original)isolated.recycle()
                                        }
                                        val packed=runCatching {
                                            GlyphScanPreviewCodec.encode(original,chosen)
                                        }.getOrDefault("")
                                        Triple(comparison.first, packed, comparison.second)
                                    }
                                    if(request == requestVersion) {
                                        result = matches
                                        qualityHints = hints
                                        if(preview.isNotEmpty()) {
                                            onSearchCompleted(
                                                GlyphScanCapture(
                                                    previewBase64=preview,
                                                    candidates=matches.map {
                                                        GlyphScanCandidate(
                                                            twkmSign=it.reference.twkmSign,
                                                            reading=it.reference.reading,
                                                            englishGloss=it.reference.english,
                                                            visualOverlap=it.visualOverlap.toDouble(),
                                                            scholarlyLevel=it.reference.scholarlyLevel,
                                                        )
                                                    },
                                                )
                                            )
                                        } else {
                                            error="Visual comparison finished, but the photo preview " +
                                                "could not be saved to History."
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (invalid: IllegalArgumentException) {
                                    if (request == requestVersion) error = invalid.message ?: "Crop one complete sign and retry."
                                } catch (_: Exception) {
                                    if(request == requestVersion) {
                                        error="Unable to search this photo. Crop one complete sign and retry."
                                    }
                                }finally {
                                    if(request == requestVersion) working=false
                                }
                            }
                        },
                        enabled=!busy && !working,
                        modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),
                    ) {
                        Text(if (working) "Comparing with the archive…" else "Compare with the archive")
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                    qualityHints.forEach { hint ->
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.medium) {
                            Text(hint, Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    result?.let { rawMatches ->
                        val matches = rawMatches.take(1).filter { GlyphDisplayPolicy.accepts(it.visualOverlap) }
                        if (rawMatches.isNotEmpty() && matches.isEmpty()) {
                            Text("No match found", style = MaterialTheme.typography.titleLarge)
                            Text("No reference exceeded 85% visual similarity.")
                        } else if (matches.isEmpty()) {
                            Text("No usable sign strokes found. Try even lighting, a sharper photo, " +
                                "and a crop around one complete sign.")
                        } else {
                            val identical = matches.first().identicalNormalizedDrawing
                            Text(
                                if (identical) "Same normalized reference drawing" else "Nearest reference drawing",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                if (identical) "Same reference drawing. Meaning depends on context."
                                else "Closest visual match. Please verify the sign.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("Similarity measures appearance, not confidence.",
                                style = MaterialTheme.typography.bodySmall)
                            matches.take(MAX_CLOSEST_REFERENCES).forEachIndexed { rank, match ->
                                EnglishMeaningMatchRow(glossary, match, rank + 1)
                            }
                            Text("Drawings: Christian Prager / University of Bonn · CC BY 4.0 · TWKM sign catalogue",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (!browseOnly && !matchOnly) OutlinedButton(
                    onClick = { browse=!browse },
                    modifier=Modifier.fillMaxWidth(),
                ) { Text(if(browse) "Hide word-sign meanings" else "Browse English word meanings") }
                if (browse && !matchOnly) {
                    OutlinedTextField(
                        modifier=Modifier.fillMaxWidth(),
                        value=searchText,
                        onValueChange={searchText=it;page=0},
                        singleLine=true,
                        label={Text("Search meanings or readings")},
                    )
                    val filtered=remember(glossary,searchText) {
                        val q=searchText.trim().lowercase(Locale.ROOT)
                        glossary.distinctSigns.filter {
                            q.isBlank() || it.english.lowercase(Locale.ROOT).contains(q) ||
                                it.reading.lowercase(Locale.ROOT).contains(q)
                        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.english })
                    }
                    val current = ListPaging.safePage(page + 1, filtered.size)
                    Text("${filtered.size} entries", style = MaterialTheme.typography.labelLarge)
                    ListPaginationControls(filtered.size, current, { page = it - 1 })
                    if (filtered.isEmpty()) Text("No matching entries")
                    if (filtered.isNotEmpty()) {
                        var expandedSign by remember(current, searchText) { mutableStateOf<Int?>(null) }
                        GlyphTable("English meaning", "Maya reading") {
                            ListPaging.pageItems(filtered, current).forEachIndexed { index, ref ->
                                val expanded = expandedSign == ref.twkmSign
                                GlyphTableRow(
                                    primary = ref.english,
                                    secondary = ref.reading,
                                    shaded = index % 2 == 1,
                                    onClick = { expandedSign = if (expanded) null else ref.twkmSign },
                                ) {
                                    val art = remember(glossary, ref.offset) { glossary.imageFor(ref) }
                                    Image(
                                        art.asImageBitmap(),
                                        contentDescription = "Drawing of ${ref.reading}",
                                        modifier = Modifier.size(48.dp).background(Color.White, RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                if (expanded) EnglishMeaningLearningDetails(ref)
                            }
                        }
                    }
                }
                if (browseOnly) Text(
                    "TWKM / University of Bonn · Drawings by Christian Prager (CC BY 4.0). " +
                        "Reading evidence levels are scholarly assessments, not image accuracy.",
                    style=MaterialTheme.typography.bodySmall,
                )
                if (browseOnly) TextButton(onClick={uri.openUri(
                    "https://classicmayan.org/portal/signcatalog")}) {
                    Text("Explore the original TWKM sign catalogue")
                }
            }
        }
    }
}

@Composable
internal fun EnglishMeaningLearningDetails(ref: GlyphEnglishReference) {
    val known=remember(ref.twkmSign) { GlyphLearningNotes.forSign(ref.twkmSign) ?: GlyphMoreLearningNotes.forSign(ref.twkmSign) }
    if(known != null) {
        GlyphLearningDetails(ref.twkmSign,initialExpanded=true)
    } else {
        Column(Modifier.fillMaxWidth().padding(start=6.dp,end=6.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("English meaning explained",style=MaterialTheme.typography.titleSmall)
            Text(
                "The TWKM catalogue links the reading ${ref.reading} to " +
                    "the proposed English meaning ${ref.english}. " +
                    "Several glosses separated by a vertical bar are possible senses, " +
                    "not a sentence translation.",
                style=MaterialTheme.typography.bodyMedium,
            )
            Text("What kind of sign?",style=MaterialTheme.typography.titleSmall)
            Text("${ref.kind}. A logogram may represent a word or meaningful word element. " +
                "Some glyph blocks combine several logograms and syllabic signs.",
                style=MaterialTheme.typography.bodyMedium)
            Text("Scholarship vs recognition",style=MaterialTheme.typography.titleSmall)
            Text(
                "TWKM reading evidence level: ${ref.scholarlyLevel} (1 is most established; " +
                    "8 is most speculative). This evaluates the scholarly reading, not " +
                    "whether your photo contains this sign. Compare the whole inscription.",
                style=MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun EnglishMeaningMatchRow(
    catalogue: GlyphEnglishMeaningCatalogue,
    match: GlyphEnglishMatch,
    rank: Int? = null,
    showArtwork: Boolean = true,
) {
    val ref=match.reference

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(rank?.let { "REFERENCE  /  %02d".format(it) } ?: "REFERENCE DRAWING",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("Visual similarity ${GlyphDisplayPolicy.percent(match.visualOverlap)}",
                    style = MaterialTheme.typography.labelMedium)
            }
            if (showArtwork) ReferenceDrawing(ref.graph,"Reference drawing for ${ref.reading}",
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 200.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp),
                fallback = { catalogue.imageFor(ref) })
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${ref.reading} · Proposed reading", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(ref.english, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text("TWKM #${ref.twkmSign} · Reference drawing", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ref.drawingSource?.let { source ->
                Text("Drawing by ${source.creator} · ${source.license}\n${source.adaptation}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            var expanded by remember(ref.twkmSign) { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Close reference notes" else "Reference details")
            }
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column { EnglishMeaningLearningDetails(ref) }
            }
        }
    }
}
