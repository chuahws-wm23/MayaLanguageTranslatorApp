package com.chuahws.mayalanguageapp.presentation.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.SavedVocabularyItem
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.SoftBadge
import com.chuahws.mayalanguageapp.presentation.components.SpeakButton

@Composable
fun SavedScreen(
    items: List<SavedVocabularyItem>,
    onRemove: (SavedVocabularyItem) -> Unit,
) {
    var query by rememberSaveable {
        mutableStateOf("")
    }
    var category by rememberSaveable {
        mutableStateOf("All")
    }
    var pendingRemoval by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var selectedSavedId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val selectedItem = items.firstOrNull {
        it.savedId == selectedSavedId
    }

    if (selectedItem != null) {
        AlertDialog(
            onDismissRequest = {
                selectedSavedId = null
            },
            title = {
                Text("Saved word")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = selectedItem.mayaText,
                        style =
                            MaterialTheme.typography
                                .headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color =
                            MaterialTheme.colorScheme
                                .primary,
                    )
                    Text(
                        text = selectedItem.englishText,
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                    )

                    selectedItem.partOfSpeech
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            SoftBadge(
                                text =
                                    it.replaceFirstChar {
                                        char ->
                                        char.uppercase()
                                    }
                            )
                        }

                    selectedItem.pronunciation
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            Text(
                                text =
                                    "Pronunciation: $it",
                                style =
                                    MaterialTheme.typography
                                        .bodySmall,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                            )
                        }

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Listen",
                            modifier =
                                Modifier.weight(1f),
                            fontWeight =
                                FontWeight.SemiBold,
                        )
                        SpeakButton(
                            text =
                                selectedItem.mayaText,
                            languageTag = "yua",
                            showUnavailableLabel = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedSavedId = null
                    },
                ) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedSavedId = null
                        pendingRemoval =
                            selectedItem.savedId
                    },
                ) {
                    Text(
                        text = "Remove",
                        color =
                            MaterialTheme.colorScheme
                                .error,
                    )
                }
            },
        )
    }

    val filtered = items.filter { item ->
        val matchesQuery =
            query.isBlank() ||
                item.mayaText.contains(query, ignoreCase = true) ||
                item.englishText.contains(query, ignoreCase = true)

        val pos = item.partOfSpeech.orEmpty()
        val matchesCategory = when (category) {
            "Nouns" -> pos.equals("noun", ignoreCase = true)
            "Verbs" -> pos.equals("verb", ignoreCase = true)
            "Adjectives" -> pos.equals("adjective", ignoreCase = true)
            else -> true
        }

        matchesQuery && matchesCategory
    }

    val itemToRemove = items.firstOrNull {
        it.savedId == pendingRemoval
    }

    if (itemToRemove != null) {
        AlertDialog(
            onDismissRequest = {
                pendingRemoval = null
            },
            title = {
                Text("Remove saved word?")
            },
            text = {
                Text(
                    "“${itemToRemove.mayaText}” will be removed from your saved vocabulary."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(itemToRemove)
                        pendingRemoval = null
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    AppGradientBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 18.dp,
                        top = 18.dp,
                        end = 18.dp,
                    ),
                label = {
                    Text("Search saved words")
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(
                            onClick = {
                                query = ""
                            },
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear search",
                            )
                        }
                    }
                } else {
                    null
                },
            )

            Row(
                modifier = Modifier.padding(
                    horizontal = 18.dp,
                    vertical = 10.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "All",
                    "Nouns",
                    "Verbs",
                    "Adjectives",
                ).forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = {
                            category = item
                        },
                        label = {
                            Text(item)
                        },
                    )
                }
            }

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (items.isEmpty()) {
                            "No saved words yet"
                        } else {
                            "No matching saved words"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        text = if (items.isEmpty()) {
                            "Save useful words from translation results."
                        } else {
                            "Try another search or filter."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        bottom = 112.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = filtered,
                        key = {
                            it.savedId
                        },
                    ) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                selectedSavedId =
                                    item.savedId
                            },
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 2.dp,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = item.mayaText,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = item.englishText,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    item.partOfSpeech
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let {
                                            SoftBadge(
                                                text = it.replaceFirstChar { c ->
                                                    c.uppercase()
                                                }
                                            )
                                        }
                                }

                                SpeakButton(
                                    text = item.mayaText,
                                    languageTag = "yua",
                                )

                                IconButton(
                                    onClick = {
                                        pendingRemoval = item.savedId
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove saved word",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
