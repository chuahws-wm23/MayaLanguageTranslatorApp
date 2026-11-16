package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import com.chuahws.mayalanguageapp.domain.service.ListPaging
import com.chuahws.mayalanguageapp.presentation.components.ListPaginationControls
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Additional OFFLINE English meaning reference search, not another trained
 * classification head. Its nearest visual match can be incorrect even when the
 * scholarly logogram reading itself has a strong evidence rating.
 */
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
    var requestVersion by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(bitmap,crop) {
        requestVersion++
        result = null
        error = null
        working = false
    }

    Card(Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if (browseOnly) "English meanings" else "Find a possible English meaning",
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
                            scope.launch {
                                try {
                                    val (matches,preview)=withContext(Dispatchers.Default) {
                                        val (x,y,w,h)=chosen.pixels(original.width,original.height)
                                        val isolated=Bitmap.createBitmap(original,x,y,w,h)
                                        val matchList = try {
                                            glossary.search(isolated,phonograms,1)
                                        } finally {
                                            if(isolated!==original)isolated.recycle()
                                        }
                                        val packed=runCatching {
                                            GlyphScanPreviewCodec.encode(original,chosen)
                                        }.getOrDefault("")
                                        matchList to packed
                                    }
                                    if(request == requestVersion) {
                                        result = matches
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
                                }catch(_:Exception){
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
                        Text(if (working) "Looking for a match…" else "Find a match")
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    result?.let { matches ->
                        val best = matches.firstOrNull()
                        if (best == null) {
                            Text("No match found. Try a clearer photo or crop around one glyph.")
                        } else {
                            EnglishMeaningMatchRow(glossary, best)
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
                        label={Text("Find a meaning or reading: e.g. lord, fire, AJAW")},
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
                    ListPaging.pageItems(filtered, current).forEach { ref ->
                        var learning by remember(ref.twkmSign) { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            val art=remember(glossary,ref.offset) { glossary.imageFor(ref) }
                            Image(art.asImageBitmap(),contentDescription="TWKM ${ref.twkmSign} drawing",
                                modifier=Modifier.size(64.dp).background(Color.White,
                                    RoundedCornerShape(8.dp)),contentScale=ContentScale.Fit)
                            Column(Modifier.weight(1f)) {
                                Text(ref.english,style=MaterialTheme.typography.titleSmall,
                                    fontWeight=FontWeight.SemiBold)
                                Text(ref.reading,style=MaterialTheme.typography.bodyMedium)
                                Text("TWKM #${ref.twkmSign} · source reading level ${ref.scholarlyLevel}",
                                    style=MaterialTheme.typography.bodySmall)
                                TextButton(onClick={learning=!learning}) {
                                    Text(if(learning) "Hide details" else "Meaning & learning notes")
                                }
                            }
                        }
                        if(learning) EnglishMeaningLearningDetails(ref)
                        HorizontalDivider()
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
) {
    val ref=match.reference
    val example=remember(catalogue,ref.offset) { catalogue.imageFor(ref) }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Closest visual match", style = MaterialTheme.typography.labelLarge)
            Image(
                example.asImageBitmap(),
                contentDescription = "Reference drawing for ${ref.reading}",
                modifier = Modifier.fillMaxWidth().heightIn(min = 144.dp, max = 180.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
            Text(ref.english, style = MaterialTheme.typography.headlineSmall)
            Text("Maya reading · ${ref.reading}", style = MaterialTheme.typography.titleMedium)
            EnglishMeaningLearningDetails(ref)
        }
    }
}
