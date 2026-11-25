package com.chuahws.mayalanguageapp.presentation.translation

import com.chuahws.mayalanguageapp.integration.glyph.displayableReference
import androidx.compose.foundation.layout.*
import com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMeaningCatalogue
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMatch

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.chuahws.mayalanguageapp.presentation.components.ReferenceDrawing

/** Exact drawing equality is not a verified historical identification. */
@Composable
internal fun GlyphReferenceResults(matches: List<GlyphEnglishMatch>, photoHints: List<String> = emptyList(), onOpenDictionary: (() -> Unit)? = null, onTryAnother: (() -> Unit)? = null,
    photo: Bitmap? = null,
    photoDescription: String = "Uploaded scan image",
    onOpenSign: ((Int, String) -> Unit)? = null,
) {
    val context=LocalContext.current
    val catalogue=remember(context) { GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context) }
    val eligible = listOfNotNull(matches.displayableReference())
    val exact=eligible.filter { it.identicalNormalizedDrawing }.singleOrNull()
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        when {
            matches.isEmpty() -> {
                Text("No readable glyph found",style=MaterialTheme.typography.headlineSmall)
                Text("Try a clearer photo of one complete glyph.")
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Photo tips", style = MaterialTheme.typography.titleMedium)
                        photoHints.ifEmpty { listOf(
                            "Use soft, even light and keep the camera steady.",
                            "Include the whole glyph, with a little space around it.",
                        ) }.distinct().take(3).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            eligible.isEmpty() -> {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No match found", style = MaterialTheme.typography.titleLarge)
                        if (GlyphDisplayPolicy.accepts(matches.first().visualOverlap)) {
                            Text("Several references look too similar to suggest one reliably.", style = MaterialTheme.typography.bodyMedium)
                        } else Text("No reference exceeded 85% visual similarity.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            exact!=null -> {
                Text("Reference drawing matched",style=MaterialTheme.typography.headlineSmall)
                Text("Same reference drawing. Meaning depends on context.",
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if (catalogue != null) {
                    if (photo != null) ReferenceComparison(photo, photoDescription, catalogue, exact)
                    EnglishMeaningMatchRow(catalogue, exact, showArtwork = photo == null)
                }
            }
            else -> {
                Surface(color=MaterialTheme.colorScheme.tertiaryContainer,shape=MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("Closest reference found",style=MaterialTheme.typography.titleLarge)
                        Text("Visual match, not a confirmed reading.",
                            style=MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Nearest reference",style=MaterialTheme.typography.titleLarge)
                if(catalogue!=null)eligible.take(MAX_CLOSEST_REFERENCES).forEachIndexed { rank,match ->
                    if (photo != null) ReferenceComparison(photo, photoDescription, catalogue, match)
                    EnglishMeaningMatchRow(catalogue, match, rank + 1, showArtwork = photo == null)
                }
            }
        }
        if (eligible.isEmpty()) ScanRecoveryActions(onTryAnother, onOpenDictionary)
        eligible.firstOrNull()?.reference?.let { reference ->
            if (onOpenSign != null) {
                OutlinedButton(onClick = { onOpenSign(reference.twkmSign, reference.graph) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open reference in dictionary")
                }
            }
        }
    }
}

@Composable
private fun ReferenceComparison(photo: Bitmap, photoDescription: String, catalogue: GlyphEnglishMeaningCatalogue, match: GlyphEnglishMatch) {
    val reference = match.reference
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your scan", style = MaterialTheme.typography.labelLarge)
            Surface(shape = MaterialTheme.shapes.medium, color = Color.White) {
                Image(photo.asImageBitmap(), photoDescription,
                    Modifier.fillMaxWidth().height(124.dp).padding(8.dp), contentScale = ContentScale.Fit)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TWKM #${reference.twkmSign}", style = MaterialTheme.typography.labelLarge)
            Surface(shape = MaterialTheme.shapes.medium, color = Color.White) {
                ReferenceDrawing(reference.graph, "Reference drawing for ${reference.reading}",
                    Modifier.fillMaxWidth().height(124.dp).padding(8.dp)) { catalogue.imageFor(reference) }
            }
        }
    }
}

@Composable
internal fun ScanRecoveryActions(onTryAnother: (() -> Unit)?, onOpenDictionary: (() -> Unit)?) {
    if (onTryAnother != null) {
        Button(onClick = onTryAnother, modifier = Modifier.fillMaxWidth()) {
            Text("Try another photo")
        }
    }
    if (onOpenDictionary != null) {
        OutlinedButton(onClick = onOpenDictionary, modifier = Modifier.fillMaxWidth()) {
            Text("Browse dictionary")
        }
        Text("Open Dictionary tools and choose Find by visible clues to search by shape.",
            style = MaterialTheme.typography.bodySmall)
    }
}
