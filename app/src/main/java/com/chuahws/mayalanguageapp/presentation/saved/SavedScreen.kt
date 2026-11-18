package com.chuahws.mayalanguageapp.presentation.saved

import com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.chuahws.mayalanguageapp.presentation.components.ListPaginationControls
import com.chuahws.mayalanguageapp.presentation.components.TranslationDetailsDialog
import com.chuahws.mayalanguageapp.presentation.components.GlyphScanThumbnail
import com.chuahws.mayalanguageapp.presentation.components.GlyphScanDetailsDialog
import com.chuahws.mayalanguageapp.domain.service.ListPaging

@Composable
fun SavedScreen(
    items: List<SavedVocabularyItem>,
    onRemove: (SavedVocabularyItem) -> Unit,
    onTranslate: (SavedVocabularyItem) -> Unit,
) {
    val displayItems = items.map { item ->
        if (!item.glyphScanRecord) item else {
            val visible = GlyphDisplayPolicy.visible(item.glyphCandidates)
            val sameBest = visible.firstOrNull() != null && visible.firstOrNull() == item.glyphCandidates.firstOrNull()
            item.copy(glyphCandidates = visible,
                mayaText = if (sameBest) item.mayaText else visible.firstOrNull()?.reading ?: "No match found",
                englishText = if (sameBest) item.englishText else visible.firstOrNull()?.englishGloss ?: "No reference above 85% similarity")
        }
    }
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
    var currentPage by rememberSaveable {
        mutableIntStateOf(1)
    }
    val listState = rememberLazyListState()

    val selectedItem = displayItems.firstOrNull {
        it.savedId == selectedSavedId
    }

    if (selectedItem != null && selectedItem.glyphScanRecord) {
        GlyphScanDetailsDialog(
            title = "Saved Maya glyph",
            imageBase64 = selectedItem.glyphImageBase64,
            candidates = selectedItem.glyphCandidates,
            secondaryText = "Favourite glyph scan · proposed matches only",
            scanKind = selectedItem.glyphScanKind,
            onDismiss = { selectedSavedId = null },
            actionLabel = "Remove favourite",
            onAction = {
                selectedSavedId = null
                pendingRemoval = selectedItem.savedId
            },
        )
    } else if (selectedItem != null) {
        TranslationDetailsDialog(
            title = "Saved word",
            sourceLabel = "Yucatec Maya",
            sourceText = selectedItem.mayaText,
            translationLabel = "English",
            translationText = selectedItem.englishText,
            metadata = selectedItem.partOfSpeech
                ?.replaceFirstChar { it.uppercase() }
                ?.takeIf(String::isNotBlank)
                ?: "Saved vocabulary",
            pronunciation = selectedItem.pronunciation,
            spokenMayaText = selectedItem.mayaText,
            onDismiss = { selectedSavedId = null },
            onTranslate = {
                selectedSavedId = null
                onTranslate(selectedItem)
            },
            onDelete = {
                selectedSavedId = null
                pendingRemoval = selectedItem.savedId
            },
        )
    }

    val filtered = displayItems.filter { item ->
        val matchesQuery =
            query.isBlank() ||
                item.mayaText.contains(query, ignoreCase = true) ||
                item.englishText.contains(query, ignoreCase = true) ||
                (item.glyphScanRecord && item.glyphCandidates.any { match ->
                    match.reading.contains(query, ignoreCase = true) ||
                        match.englishGloss.contains(query, ignoreCase = true)
                })

        val pos = item.partOfSpeech.orEmpty()
        val matchesCategory = when (category) {
            "Glyph scans" -> item.glyphScanRecord
            "Words" -> !item.glyphScanRecord
            "Nouns" -> !item.glyphScanRecord && pos.equals("noun", ignoreCase = true)
            "Verbs" -> !item.glyphScanRecord && pos.equals("verb", ignoreCase = true)
            "Adjectives" -> !item.glyphScanRecord && pos.equals("adjective", ignoreCase = true)
            else -> true
        }

        matchesQuery && matchesCategory
    }

    val displayPage = ListPaging.safePage(currentPage, filtered.size)
    val visibleItems = ListPaging.pageItems(filtered, displayPage)

    // Keep navigation valid when a saved word is removed.
    LaunchedEffect(filtered.size) {
        currentPage = ListPaging.safePage(currentPage, filtered.size)
    }
    LaunchedEffect(displayPage, query, category) {
        listState.scrollToItem(0)
    }

    val itemToRemove = displayItems.firstOrNull {
        it.savedId == pendingRemoval
    }

    if (itemToRemove != null) {
        AlertDialog(
            onDismissRequest = {
                pendingRemoval = null
            },
            title = {
                Text(if (itemToRemove.glyphScanRecord) "Remove favourite glyph?" else "Remove saved word?")
            },
            text = {
                Text(
                    if (itemToRemove.glyphScanRecord)
                        "Remove this glyph scan from favourites? Its History record will remain."
                    else "“${itemToRemove.mayaText}” will be removed from your saved vocabulary."
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
                    currentPage = 1
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 18.dp,
                        top = 18.dp,
                        end = 18.dp,
                    ),
                label = {
                    Text("Search favourite glyphs or words")
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
                                currentPage = 1
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
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "All", "Glyph scans", "Words",
                    "Nouns", "Verbs", "Adjectives",
                ).forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = {
                            category = item
                            currentPage = 1
                        },
                        label = {
                            Text(item)
                        },
                    )
                }
            }

            if (filtered.size > ListPaging.PAGE_SIZE) {
                ListPaginationControls(
                    totalItems = filtered.size,
                    currentPage = displayPage,
                    onPageSelected = { selected ->
                        currentPage = ListPaging.safePage(
                            selected, filtered.size,
                        )
                    },
                    modifier = Modifier.padding(
                        horizontal = 18.dp,
                        vertical = 4.dp,
                    ),
                )
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
                            "No favourites yet"
                        } else {
                            "No matching favourites"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        text = if (items.isEmpty()) {
                            "Save a scan or vocabulary item to build your collection."
                        } else {
                            "Try another search or filter."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        bottom = 112.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = visibleItems,
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
                                defaultElevation = 0.dp,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (item.glyphScanRecord) {
                                    Surface(shape = MaterialTheme.shapes.medium) {
                                        GlyphScanThumbnail(item.glyphImageBase64, side = 96.dp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = if (item.glyphScanRecord)
                                            "Glyph · ${item.mayaText}"
                                        else item.mayaText,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = if (item.glyphScanRecord)
                                            "Possible: ${item.englishText} (unverified)"
                                        else item.englishText,
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

                                if (!item.glyphScanRecord) {
                                    SpeakButton(
                                        text = item.mayaText,
                                        languageTag = "yua",
                                    )
                                }

                                if (!item.glyphScanRecord) {
                                    IconButton(onClick = { pendingRemoval = item.savedId }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove saved word")
                                    }
                                }
                            }
                        }
                    }


                }
            }
        }
    }
}
