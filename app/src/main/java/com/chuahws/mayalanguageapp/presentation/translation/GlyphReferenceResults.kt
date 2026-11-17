package com.chuahws.mayalanguageapp.presentation.translation

import androidx.compose.foundation.layout.*
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
    val exact=matches.filter { it.identicalNormalizedDrawing }.singleOrNull()
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        when {
            matches.isEmpty() -> {
                Text("No readable glyph found",style=MaterialTheme.typography.headlineSmall)
                Text("No nearest references are available for this image. Try another photo with clear, complete glyph lines.")
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
            exact!=null -> {
                Text("Reference drawing matched",style=MaterialTheme.typography.headlineSmall)
                Text("The normalized drawing matches this reference. Its meaning still depends on inscription context.",
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(catalogue!=null)EnglishMeaningMatchRow(catalogue,exact)
            }
            else -> {
                Surface(color=MaterialTheme.colorScheme.tertiaryContainer,shape=MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("Closest reference found",style=MaterialTheme.typography.titleLarge)
                        Text("Compare your glyph with the references below. Visual similarity does not confirm its identity.",
                            style=MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Nearest references",style=MaterialTheme.typography.titleLarge)
                if(catalogue!=null)matches.take(5).forEachIndexed { rank,match ->
                    EnglishMeaningMatchRow(catalogue,match,rank+1)
                }
            }
        }
    }
}
