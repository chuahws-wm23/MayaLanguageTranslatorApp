package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualCatalogue
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualMatch
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualReference
import com.chuahws.mayalanguageapp.integration.glyph.MayaGlyphRecognizerV4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * Separate browseable reference library and optical search; not a translation
 * feature. This accepts a single glyph crop (manual or v11 Inspect action).
 */
@Composable
internal fun GlyphCataloguePanel(
    bitmap: Bitmap?,
    crop: GlyphCrop,
    busy: Boolean,
) {
    val context = LocalContext.current
    val catalogue = remember(context) {
        runCatching { GlyphVisualCatalogue.fromAssetsOrNull(context) }.getOrNull()
    }
    var showBrowse by remember { mutableStateOf(false) }
    var searchSound by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var showWordSigns by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<GlyphVisualMatch>?>(null) }
    var matching by remember { mutableStateOf(false) }
    var matchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(bitmap, crop) { matches = null; matchError = null }

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Explore Maya glyphs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (catalogue != null) {
                Text(
                    "Explore ${catalogue.syllables.size} proposed Maya syllable readings with " +
                        "${catalogue.references.size} TWKM reference drawings, in addition to " +
                        "the 10 signs supported by Research v4.",
                )
                Text(
                    "Syllables such as ba, ku and ma represent SOUNDS, not English word meanings. " +
                        "TWKM labels are proposed scholarly values; the pictures are " +
                        "visual references, not verified photo translations.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (bitmap != null) {
                    Button(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        onClick = {
                            matching = true
                            matchError = null
                            val original = bitmap
                            val selected = crop
                            scope.launch {
                                try {
                                    matches = withContext(Dispatchers.Default) {
                                        val (x,y,w,h) = selected.pixels(original.width,original.height)
                                        val isolated = Bitmap.createBitmap(original,x,y,w,h)
                                        try { catalogue.search(isolated,number=3) }
                                        finally { if (isolated !== original) isolated.recycle() }
                                    }
                                } catch (_: Exception) {
                                    matches = null
                                    matchError = "Could not compare this glyph. Select one clear sign and try again."
                                } finally { matching = false }
                            }
                        },
                        enabled = !busy && !matching,
                    ) {
                        Text(if (matching) "Comparing drawings…" else
                            "Find similar glyph pictures (86 syllables)")
                    }
                    Text(
                        "Uses the current single-glyph crop. For a panel containing many signs, " +
                            "run the individual-component scan and tap Inspect on one region first.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    matchError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
                    matches?.let { results ->
                        if (results.isEmpty()) {
                            Text("No usable glyph strokes found. Try a tighter, clearer crop.")
                        } else {
                            Text("Top 3 visually similar references",
                                style=MaterialTheme.typography.titleMedium,
                                fontWeight=FontWeight.SemiBold)
                            Text(
                                "Shape scores are NOT the probability that a proposed syllable " +
                                    "is correct. Even the top match may be wrong; real-photo accuracy " +
                                    "has not been established.",
                                style=MaterialTheme.typography.bodySmall,
                            )
                            results.forEachIndexed { rank, found ->
                                CatalogueMatchRow(catalogue,rank+1,found)
                            }
                        }
                    }
                }
            } else {
                Text(
                    "The larger offline drawing gallery has not been installed in this build. " +
                        "The 10-sign recognizer still works. Pull the new " +
                        "fyp-development reference assets and rebuild to enable 86 syllables.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedButton(
                onClick = { showBrowse = !showBrowse },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (showBrowse) "Hide Maya glyph reference catalogue" else "Browse Maya glyph reference catalogue") }

            if (showBrowse) {
                OutlinedButton(
                    onClick = { showWordSigns = !showWordSigns },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showWordSigns) "Hide 10 word/name sign categories" else
                        "Show 10 word/name sign categories")
                }
                if (showWordSigns) {
                    MayaGlyphRecognizerV4.supportedSigns().forEach { sign ->
                        val sample = remember(sign.signNumber) {
                            runCatching {
                                context.assets.open("research_v4_ref_${sign.signNumber}.png").use {
                                    BitmapFactory.decodeStream(it)
                                }
                            }.getOrNull()
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            sample?.let { img ->
                                Image(
                                    bitmap=img.asImageBitmap(),
                                    contentDescription="Reference for ${sign.reading}",
                                    modifier=Modifier.size(60.dp).background(Color.White, RoundedCornerShape(8.dp)),
                                    contentScale=ContentScale.Fit,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text("${sign.reading} · ${sign.englishGloss}",
                                    style=MaterialTheme.typography.bodyMedium,
                                    fontWeight=FontWeight.SemiBold)
                                Text("TWKM #${sign.signNumber} · v4 recognition category",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                        var showLearning by remember(sign.signNumber) { mutableStateOf(false) }
                        TextButton(onClick = { showLearning = !showLearning }) {
                            Text(if (showLearning) "Hide explanation" else "Learn the meaning and history")
                        }
                        if (showLearning) {
                            GlyphLearningDetails(signNumber = sign.signNumber, initialExpanded = true)
                        }
                    }
                }

                if (catalogue != null) {
                    OutlinedTextField(
                        value=searchSound,
                        onValueChange={ searchSound=it;page=0 },
                        modifier=Modifier.fillMaxWidth(),
                        label={ Text("Search syllable (e.g. ba, ku, ma)") },
                        singleLine=true,
                    )
                    val soundList = remember(catalogue,searchSound) {
                        catalogue.syllables.filter { it.contains(searchSound.trim().lowercase()) }
                    }
                    val perPage=12
                    val maxPage=((soundList.size-1).coerceAtLeast(0))/perPage
                    val safePage=page.coerceIn(0,maxPage)
                    Text("${soundList.size} syllables found · page ${safePage+1} of ${maxPage+1}",
                        style=MaterialTheme.typography.bodySmall)
                    soundList.drop(safePage*perPage).take(perPage).forEach { sound ->
                        val variants = remember(catalogue,sound) { catalogue.forSyllable(sound) }
                        val preview = remember(catalogue,sound) {
                            variants.firstOrNull()?.let { catalogue.referenceBitmap(it) }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(12.dp),
                        ) {
                            preview?.let {
                                Image(
                                    bitmap=it.asImageBitmap(),
                                    contentDescription="Proposed Maya phonogram ${sound}",
                                    modifier=Modifier.size(60.dp).background(Color.White, RoundedCornerShape(8.dp)),
                                    contentScale=ContentScale.Fit,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(sound, style=MaterialTheme.typography.titleMedium,
                                    fontWeight=FontWeight.SemiBold)
                                Text("${variants.size} drawing(s) · Example TWKM #${variants.firstOrNull()?.twkmSign}",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton(onClick={page=(safePage-1).coerceAtLeast(0)},
                            enabled=safePage>0) { Text("Previous") }
                        TextButton(onClick={page=(safePage+1).coerceAtMost(maxPage)},
                            enabled=safePage<maxPage) { Text("Next") }
                    }
                    Text(
                        "TWKM drawings: Christian Prager / University of Bonn · CC BY 4.0. " +
                            "Source: classicmayan.org/portal/signcatalog. " +
                            "Syllabogram values are proposed; verify with an epigrapher. " +
                            "The four external syllabary chart images are excluded from this app.",
                        style=MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogueMatchRow(
    catalogue: GlyphVisualCatalogue,
    number: Int,
    match: GlyphVisualMatch,
) {
    val ref=match.reference
    val bitmap=remember(catalogue,ref.offset) { catalogue.referenceBitmap(ref) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp),
        ) {
            Image(
                bitmap=bitmap.asImageBitmap(),
                contentDescription="TWKM glyph drawing ${ref.twkmSign} for proposed syllable ${ref.syllable}",
                modifier=Modifier.size(84.dp).background(Color.White, RoundedCornerShape(8.dp)),
                contentScale=ContentScale.Fit,
            )
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text("${number}. Possible sound: ${ref.syllable}",
                    style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.SemiBold)
                Text("TWKM #${ref.twkmSign} · drawing ${ref.graph}",
                    style=MaterialTheme.typography.bodySmall)
                Text(
                    "Visual overlap ${"%.0f".format(match.visualSimilarity*100)}% — not model confidence",
                    style=MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
