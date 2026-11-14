package com.chuahws.mayalanguageapp.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection

/**
 * Explain incomplete OCR/dictionary results in ordinary language.
 * Suggested spelling is never accepted until the user taps a suggestion.
 */
@Composable
fun TranslationGuidanceCard(
    advice: String?,
    unknownTerms: List<String>,
    ambiguousTerms: List<String>,
    unreliableSegments: List<String>,
    suggestions: List<DictionaryEntry>,
    direction: TranslationDirection,
    onSuggestionSelected: (String) -> Unit,
) {
    if (
        advice.isNullOrBlank() &&
        unknownTerms.isEmpty() &&
        ambiguousTerms.isEmpty() &&
        unreliableSegments.isEmpty() &&
        suggestions.isEmpty()
    ) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = "Check your translation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            advice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (unknownTerms.isNotEmpty()) {
                Text(
                    text = "Not found in the dictionary: " +
                        unknownTerms.distinct().take(5).joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (ambiguousTerms.isNotEmpty()) {
                Text(
                    text = "More than one possible meaning: " +
                        ambiguousTerms.distinct().take(4).joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (unreliableSegments.isNotEmpty()) {
                Text(
                    text = "No reliable sentence translation: " +
                        unreliableSegments.take(2).joinToString("; "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (suggestions.isNotEmpty()) {
                Text(
                    text = "Possible spelling matches",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                suggestions.take(3).forEach { entry ->
                    val source = when (direction) {
                        TranslationDirection.MAYA_TO_ENGLISH -> entry.mayaText
                        TranslationDirection.ENGLISH_TO_MAYA -> entry.englishText
                    }
                    val target = when (direction) {
                        TranslationDirection.MAYA_TO_ENGLISH -> entry.englishText
                        TranslationDirection.ENGLISH_TO_MAYA -> entry.mayaText
                    }

                    OutlinedButton(
                        onClick = { onSuggestionSelected(source) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "$source  ·  $target",
                            maxLines = 2,
                        )
                    }
                }
                Text(
                    text = "Tap a suggestion to correct the input, then translate again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
