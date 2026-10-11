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
import com.chuahws.mayalanguageapp.presentation.components.SpeakButton

@Composable
fun SavedScreen(
    items: List<SavedVocabularyItem>,
    onRemove: (SavedVocabularyItem) -> Unit,
) {
    var query by rememberSaveable {
        mutableStateOf("")
    }
    var pendingRemoval by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val filtered = items.filter {
        query.isBlank() ||
            it.mayaText.contains(
                query,
                ignoreCase = true,
            ) ||
            it.englishText.contains(
                query,
                ignoreCase = true,
            )
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
                .padding(20.dp),
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

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.BookmarkBorder,
                    contentDescription = null,
                )
                Text(
                    text = if (items.isEmpty()) {
                        "No saved words yet"
                    } else {
                        "No matching saved words"
                    },
                    style =
                        MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = if (items.isEmpty()) {
                        "Save a word from a translation result."
                    } else {
                        "Try a different search term."
                    },
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = filtered,
                    key = {
                        it.savedId
                    },
                ) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = item.mayaText,
                                    style =
                                        MaterialTheme.typography.titleMedium,
                                    fontWeight =
                                        FontWeight.SemiBold,
                                )
                                Text(
                                    text = item.englishText,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier =
                                        Modifier.padding(top = 3.dp),
                                )
                            }

                            SpeakButton(
                                text = item.mayaText,
                                languageTag = "yua",
                            )

                            IconButton(
                                onClick = {
                                    pendingRemoval =
                                        item.savedId
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription =
                                        "Remove saved word",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
