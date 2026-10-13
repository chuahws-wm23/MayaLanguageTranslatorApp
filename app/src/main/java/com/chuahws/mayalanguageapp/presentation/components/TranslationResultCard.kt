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

@Composable
fun TranslationResultCard(
    output: String,
    entry: DictionaryEntry?,
    outputLanguageTag: String,
    onSave: (() -> Unit)?,
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
                        text = "Maya Translation",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = output,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
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

                SpeakButton(
                    text = output,
                    languageTag = outputLanguageTag,
                )

                if (onSave != null && entry != null) {
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
