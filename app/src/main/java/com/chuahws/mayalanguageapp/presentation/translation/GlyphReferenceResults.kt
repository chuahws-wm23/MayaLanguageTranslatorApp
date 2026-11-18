package com.chuahws.mayalanguageapp.presentation.translation

import androidx.compose.foundation.layout.*
import com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMeaningCatalogue
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMatch

/** Exact drawing equality is not a verified historical identification. */
@Composable
internal fun GlyphReferenceResults(matches: List<GlyphEnglishMatch>, photoHints: List<String> = emptyList()) {
    val context=LocalContext.current
    val catalogue=remember(context) { GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context) }
    val eligible = matches.take(1).filter { GlyphDisplayPolicy.accepts(it.visualOverlap) }
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
                        Text("No reference exceeded 85% visual similarity.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            exact!=null -> {
                Text("Reference drawing matched",style=MaterialTheme.typography.headlineSmall)
                Text("Same reference drawing. Meaning depends on context.",
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(catalogue!=null)EnglishMeaningMatchRow(catalogue,exact)
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
                    EnglishMeaningMatchRow(catalogue,match,rank+1)
                }
            }
        }
    }
}
