package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import com.chuahws.mayalanguageapp.domain.service.ListPaging
import com.chuahws.mayalanguageapp.presentation.components.ListPaginationControls
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
import com.chuahws.mayalanguageapp.integration.glyph.MayaGlyphRecognizerV4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Separate browseable reference library and optical search; not a translation
 * feature. This accepts a single glyph crop (manual or v11 Inspect action).
 */
@Composable
internal fun GlyphCataloguePanel(
    bitmap: Bitmap?,
    crop: GlyphCrop,
    busy: Boolean,
    browseOnly: Boolean = false,
    matchOnly: Boolean = false,
) {
    require(!browseOnly || !matchOnly)
    val context = LocalContext.current
    val catalogue = remember(context) {
        runCatching { GlyphVisualCatalogue.fromAssetsOrNull(context) }.getOrNull()
    }
    var showBrowse by remember(browseOnly) { mutableStateOf(browseOnly) }
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
                if (browseOnly) "Maya syllable sounds" else "Find similar syllable drawings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (catalogue != null) {
                if (browseOnly) Text("Explore the sounds written with Maya syllable signs.")
                if (!browseOnly && bitmap != null) {
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
                                        try { catalogue.search(isolated, number = 1) }
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
                        Text(if (matching) "Comparing drawings…" else "Find a syllable match")
                    }
                    matchError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
                    matches?.let { results ->
                        if (results.isEmpty()) {
                            Text("No usable glyph strokes found. Try a tighter, clearer crop.")
                        } else {
                            Text("Closest sound match", style = MaterialTheme.typography.titleMedium)
                            CatalogueMatchRow(catalogue, results.first())
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

            if (!browseOnly && !matchOnly) OutlinedButton(
                onClick = { showBrowse = !showBrowse },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (showBrowse) "Hide Maya glyph reference catalogue" else "Browse Maya glyph reference catalogue") }

            if (showBrowse && !matchOnly) {
                if (!browseOnly) OutlinedButton(
                    onClick = { showWordSigns = !showWordSigns },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showWordSigns) "Hide 10 word/name sign categories" else
                        "Show 10 word/name sign categories")
                }
                if (showWordSigns && !browseOnly) {
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
                        catalogue.syllables.filter { it.contains(searchSound.trim(), ignoreCase = true) }.sorted()
                    }
                    val safePage = ListPaging.safePage(page + 1, soundList.size)
                    Text("${soundList.size} entries", style = MaterialTheme.typography.labelLarge)
                    ListPaginationControls(soundList.size, safePage, { page = it - 1 })
                    if (soundList.isEmpty()) Text("No matching entries")
                    ListPaging.pageItems(soundList, safePage).forEach { sound ->
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
    match: GlyphVisualMatch,
) {
    val ref=match.reference
    val bitmap=remember(catalogue,ref.offset) { catalogue.referenceBitmap(ref) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Reference drawing for ${ref.syllable}",
                modifier = Modifier.fillMaxWidth().height(144.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
            Text(ref.syllable, style = MaterialTheme.typography.headlineSmall)
            Text("This glyph represents the syllable ${ref.syllable}. Maya scribes combined syllable signs to spell words.")
        }
    }
}
