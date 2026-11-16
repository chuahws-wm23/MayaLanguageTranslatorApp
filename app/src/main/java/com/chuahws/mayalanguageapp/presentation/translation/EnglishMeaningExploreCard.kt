package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
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
                Text(
                    if (browseOnly) "Browse ${glossary.distinctSigns.size} source-linked word signs, " +
                        "such as lord, fire, leaf and mist." else
                        "Compare your selected glyph against ${glossary.references.size} drawings " +
                            "from ${glossary.distinctSigns.size} word-sign entries.",
                )
                if (browseOnly) Text(
                    "A logogram represents a word or meaningful word element; the English sense " +
                        "depends on its context.",
                    style=MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (browseOnly) "These are proposed scholarly meanings, not results confirmed " +
                        "from a photograph." else "Visual matches are unverified. In a small chart " +
                        "study, only 10/135 first matches were correct. Inspect all suggestions.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                                            glossary.search(isolated,phonograms,3)
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
                        Text(if(working) "Comparing English word-sign references…" else
                            "Find matching English word signs")
                    }
                    Text(
                        "Best for one complete sign. For multiple signs, use the component scanner. " +
                            "Matching also saves a small cropped photo preview and possible readings " +
                            "to your account History; delete the record there at any time.",
                        style=MaterialTheme.typography.bodySmall,
                    )
                    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                    result?.let { list ->
                        Text("Closest word-sign drawings",style=MaterialTheme.typography.titleMedium)
                        Text(
                            "These are possible visual matches, not confirmed English translations. " +
                                "Overlap scores are NOT percentage confidence. " +
                                "A source reading's evidence level is also different from photo accuracy.",
                            style=MaterialTheme.typography.bodySmall,
                        )
                        if(list.isEmpty())Text("No usable glyph shape found. Crop a single clearer sign.")
                        list.forEachIndexed { index, match ->
                            EnglishMeaningMatchRow(glossary,match,index+1)
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
                        }
                    }
                    val last=((filtered.size-1).coerceAtLeast(0))/10
                    val current=page.coerceIn(0,last)
                    Text("${filtered.size} matching word signs · Page ${current+1}/${last+1}",
                        style=MaterialTheme.typography.bodySmall)
                    filtered.drop(current*10).take(10).forEach { ref ->
                        var learning by remember(ref.twkmSign) { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            val art=remember(glossary,ref.offset) { glossary.imageFor(ref) }
                            Image(art.asImageBitmap(),contentDescription="TWKM ${ref.twkmSign} drawing",
                                modifier=Modifier.size(64.dp).background(Color.White,
                                    RoundedCornerShape(8.dp)),contentScale=ContentScale.Fit)
                            Column(Modifier.weight(1f)) {
                                Text(ref.reading,style=MaterialTheme.typography.titleSmall,
                                    fontWeight=FontWeight.SemiBold)
                                Text(ref.english,style=MaterialTheme.typography.bodyMedium)
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
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton(onClick={page=(current-1).coerceAtLeast(0)},
                            enabled=current>0) {Text("Previous")}
                        TextButton(onClick={page=(current+1).coerceAtMost(last)},
                            enabled=current<last) {Text("Next")}
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
    number: Int,
) {
    val ref=match.reference
    val example=remember(catalogue,ref.offset) { catalogue.imageFor(ref) }
    var learning by remember(ref.twkmSign) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Image(
                    example.asImageBitmap(),
                    contentDescription="TWKM English word sign ${ref.twkmSign} illustration",
                    modifier=Modifier.size(80.dp).background(Color.White,
                        RoundedCornerShape(8.dp)),contentScale=ContentScale.Fit,
                )
                Column(Modifier.weight(1f)) {
                    Text("${number}. ${ref.reading}",style=MaterialTheme.typography.titleMedium,
                        fontWeight=FontWeight.SemiBold)
                    Text("Possible English gloss: ${ref.english}",
                        style=MaterialTheme.typography.bodyMedium)
                    Text("TWKM #${ref.twkmSign} · ${"%.0f".format(Locale.US,match.visualOverlap*100)}% visual overlap",
                        style=MaterialTheme.typography.bodySmall)
                    Text("NOT verified translation or probability",
                        style=MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(onClick={learning=!learning}) {
                Text(if(learning)"Hide the explanation" else "Explain this meaning")
            }
            if(learning) EnglishMeaningLearningDetails(ref)
        }
    }
}
