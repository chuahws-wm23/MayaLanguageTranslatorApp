package com.chuahws.mayalanguageapp.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
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
    onSave: (() -> Unit)?,
) {
    if (output.isBlank()) return

    val clipboard = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Translation",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = output,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            entry?.pronunciation
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        text = "Pronunciation: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            entry?.partOfSpeech
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(output))
                    },
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy",
                    )
                }

                SpeakButton(text = output)

                if (onSave != null && entry != null) {
                    FilledTonalIconButton(onClick = onSave) {
                        Icon(
                            Icons.Rounded.BookmarkAdd,
                            contentDescription = "Save word",
                        )
                    }
                }
            }
        }
    }
}
