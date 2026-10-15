package com.chuahws.mayalanguageapp.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.presentation.theme.MayaGreen

@Composable
fun TranslationResultCard(
    output: String,
    entry: DictionaryEntry?,
    alternatives: List<DictionaryEntry> = emptyList(),
    outputLanguageTag: String,
    onSave: (() -> Unit)?,
    onSaveAlternative: ((DictionaryEntry) -> Unit)? = null,
) {
    if (output.isBlank()) return

    val clipboard = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccentIcon(
                    icon = Icons.Rounded.Translate,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = if (alternatives.isEmpty()) {
                            "Translation result"
                        } else {
                            "Possible translations"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (alternatives.isEmpty()) {
                        Text(
                            text = output,
                            style =
                                MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(
                            text =
                                "This input has more than one valid dictionary match.",
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (alternatives.isNotEmpty()) {
                val visibleAlternatives =
                    alternatives.take(8)

                visibleAlternatives.forEach { alternative ->
                    val translatedText =
                        if (outputLanguageTag == "en-US") {
                            alternative.englishText
                        } else {
                            alternative.mayaText
                        }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                text = translatedText,
                                style =
                                    MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )

                            alternative.partOfSpeech
                                ?.takeIf { it.isNotBlank() }
                                ?.let {
                                    SoftBadge(text = it)
                                }

                            alternative.pronunciation
                                ?.takeIf { it.isNotBlank() }
                                ?.let {
                                    Text(
                                        text = "Pronunciation: $it",
                                        style =
                                            MaterialTheme.typography.bodySmall,
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                            alternative.source
                                ?.takeIf { it.isNotBlank() }
                                ?.let {
                                    Text(
                                        text = "Source: $it",
                                        style =
                                            MaterialTheme.typography.bodySmall,
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                            if (onSaveAlternative != null) {
                                FilledTonalButton(
                                    onClick = {
                                        onSaveAlternative(alternative)
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.BookmarkAdd,
                                        contentDescription = null,
                                    )
                                    Text(
                                        text = "Save this meaning",
                                        modifier =
                                            Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (alternatives.size > visibleAlternatives.size) {
                    Text(
                        text = "+" +
                            (alternatives.size -
                                visibleAlternatives.size) +
                            " more dictionary senses",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            entry?.let { dictionaryEntry ->
                dictionaryEntry.usageLabel
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        SoftBadge(
                            text = it,
                        )
                    }

                Text(
                    text = "English meaning",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = dictionaryEntry.englishText,
                    style = MaterialTheme.typography.titleMedium,
                )

                dictionaryEntry.pronunciation
                    ?.takeIf { it.isNotBlank() }
                    ?.let { pronunciation ->
                        Text(
                            text = "Pronunciation",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = pronunciation,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                dictionaryEntry.acceptedEnglishInputs
                    .distinct()
                    .takeIf { it.size > 1 }
                    ?.let { inputs ->
                        Text(
                            text = "Also accepted",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            inputs.take(3).forEach {
                                SoftBadge(
                                    text = it,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SoftBadge(
                        text = if (dictionaryEntry.verified) {
                            "Verified entry"
                        } else {
                            "Source-derived entry"
                        },
                        color = if (dictionaryEntry.verified) {
                            MayaGreen
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                    )
                }

                dictionaryEntry.source
                    ?.takeIf { it.isNotBlank() }
                    ?.let { source ->
                        Text(
                            text = "Source: $source",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                if (outputLanguageTag == "yua") {
                    Text(
                        text = if (
                            dictionaryEntry.pronunciation
                                ?.isNotBlank() == true
                        ) {
                            "If device audio is unavailable, use the pronunciation shown above."
                        } else {
                            "Yucatec Maya speech audio depends on the text-to-speech voices installed on this device."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(output))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = null,
                    )
                    Text(
                        text = "Copy",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                if (alternatives.isEmpty()) {
                    SpeakButton(
                        text = output,
                        languageTag = outputLanguageTag,
                    )
                }

                if (
                    alternatives.isEmpty() &&
                    onSave != null &&
                    entry != null
                ) {
                    FilledTonalButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Rounded.BookmarkAdd,
                            contentDescription = null,
                        )
                        Text(
                            text = "Save",
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
