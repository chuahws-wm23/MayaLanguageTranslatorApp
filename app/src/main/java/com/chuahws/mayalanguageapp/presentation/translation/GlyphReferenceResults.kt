package com.chuahws.mayalanguageapp.presentation.translation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMeaningCatalogue
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMatch

/**
 * A photo retrieves visually similar catalogue drawings, not verified
 * inscriptions. Users can reject the suggestions or inspect an alternative.
 */
@Composable
internal fun GlyphReferenceResults(
    matches: List<GlyphEnglishMatch>,
    photoHints: List<String> = emptyList(),
    onOpenDictionary: ((Int?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val catalogue = remember(context) { GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context) }
    val choices = matches.distinctBy { it.reference.twkmSign }.take(5)
    var chosenIndex by remember(matches) { mutableIntStateOf(0) }
    var rejected by remember(matches) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            choices.isEmpty() -> {
                Text("No readable glyph found", style = MaterialTheme.typography.headlineSmall)
                Text("Try selecting one complete glyph area, or a clearer photograph.")
                Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Photo tips", style = MaterialTheme.typography.titleMedium)
                        photoHints.ifEmpty {
                            listOf("Use soft, even light and keep the camera steady.",
                                "Include the whole glyph, with a little space around it.")
                        }.distinct().take(3).forEach { Text(it) }
                    }
                }
            }
            else -> {
                if (!GlyphDisplayPolicy.accepts(choices.first().visualOverlap)) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("No match found", style = MaterialTheme.typography.titleLarge)
                            Text("The closest drawing did not pass the existing 85% visual-similarity guide. " +
                                "You can inspect nearby drawings, but none is a reliable identification.")
                        }
                    }
                } else {
                    Text(if (choices.first().identicalNormalizedDrawing)
                        "Reference drawing matched" else "Closest reference found",
                        style = MaterialTheme.typography.titleLarge)
                }
                Text("Review possible reference drawings", style = MaterialTheme.typography.titleMedium)
                Text("Choose the nearest-looking drawing to inspect. Similarity is appearance only, " +
                    "not the probability that its reading or English meaning is correct.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                choices.forEachIndexed { index, match ->
                    val ref = match.reference
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            chosenIndex = index
                            rejected = false
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (!rejected && chosenIndex == index)
                                MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RadioButton(selected = !rejected && chosenIndex == index,
                                onClick = { chosenIndex = index; rejected = false })
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${index + 1}. ${ref.reading} · ${ref.english}",
                                    style = MaterialTheme.typography.titleSmall)
                                Text("TWKM #${ref.twkmSign} · Visual similarity ${GlyphDisplayPolicy.percent(match.visualOverlap)}",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                OutlinedButton(onClick = { rejected = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("None of these match my glyph")
                }
                if (rejected) {
                    Text("No reading selected. Try adjusting the glyph area or browse the archive.",
                        style = MaterialTheme.typography.bodyMedium)
                } else {
                    val chosen = choices[chosenIndex.coerceIn(0, choices.lastIndex)]
                    Text("Selected reference for review", style = MaterialTheme.typography.titleMedium)
                    if (catalogue != null) EnglishMeaningMatchRow(catalogue, chosen, chosenIndex + 1)
                    Text("Your selection is not a verified translation and does not train the model.",
                        style = MaterialTheme.typography.bodySmall)
                    if (onOpenDictionary != null) {
                        Button(onClick = { onOpenDictionary(chosen.reference.twkmSign) },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Learn this glyph in dictionary")
                        }
                    }
                }
            }
        }
        if (onOpenDictionary != null) {
            OutlinedButton(onClick = { onOpenDictionary(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Browse full glyph dictionary")
            }
        }
    }
}
