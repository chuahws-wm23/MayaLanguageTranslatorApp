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
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val glossary = remember(context) {
        runCatching { GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context) }.getOrNull()
    }
    val phonograms = remember(context) {
        runCatching { GlyphVisualCatalogue.fromAssetsOrNull(context) }.getOrNull()
    }
    var searchText by remember { mutableStateOf("") }
    var browse by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<List<GlyphEnglishMatch>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(bitmap,crop) { result=null;error=null }

    Card(Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("English meanings & Maya history",
                style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            if(glossary==null || phonograms==null) {
                Text("The expanded word-sign catalogue is not in this build yet. " +
                    "Update the fyp-development branch and rebuild the app.",
                    style=MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    "Explore ${glossary.distinctSigns.size} documented TWKM word-sign identities " +
                        "with ${glossary.references.size} licensed drawings. " +
                        "Includes meanings such as lord, leaf, cloud/mist and fire.",
                )
                Text(
                    "A logogram carries a word or word element. This gallery is a source-backed " +
                        "reference lookup, NOT a 135-class neural recognizer. " +
                        "English glosses depend on surrounding signs.",
                    style=MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Research accuracy warning: in a small same-source drawing study, " +
                        "only 10/135 first matches and 18/135 top-three matches were correct. " +
                        "Do not use visual lookalikes as automatic English translations. " +
                        "This catalogue is best used for study and manual comparison.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if(bitmap!=null) {
                    Button(
                        onClick = {
                            val original=bitmap
                            val chosen=crop
                            error=null;result=null;working=true
                            scope.launch {
                                try {
                                    result=withContext(Dispatchers.Default) {
                                        val (x,y,w,h)=chosen.pixels(original.width,original.height)
                                        val isolated=Bitmap.createBitmap(original,x,y,w,h)
                                        try { glossary.search(isolated,phonograms,3) }
                                        finally { if(isolated!==original)isolated.recycle() }
                                    }
                                }catch(_:Exception){
                                    error="Unable to search this photo. Crop one complete sign and retry."
                                }finally{working=false}
                            }
                        },
                        enabled=!busy && !working,
                        modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),
                    ) {
                        Text(if(working) "Comparing English word-sign references…" else
                            "Find possible English meanings")
                    }
                    Text(
                        "For several glyphs in one image, first run the individual-component scan, " +
                            "then inspect a single component. One full block is not necessarily one word.",
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
                OutlinedButton(
                    onClick = { browse=!browse },
                    modifier=Modifier.fillMaxWidth(),
                ) { Text(if(browse) "Hide word-sign meanings" else "Browse English word meanings") }
                if(browse) {
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
                Text(
                    "TWKM / University of Bonn · Drawings by Christian Prager (CC BY 4.0). " +
                        "Sign readings are source-proposed. 1–3 refers to the catalogue's " +
                        "scholarly evidence level, NOT the AI's visual confidence.",
                    style=MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick={uri.openUri(
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
