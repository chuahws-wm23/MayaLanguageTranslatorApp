package com.chuahws.mayalanguageapp.presentation.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.translation.EnglishMeaningExploreCard
import com.chuahws.mayalanguageapp.presentation.translation.GlyphCataloguePanel

/**
 * Dedicated dictionary destination, reachable via both sidebar and bottom bar.
 *
 * The picture scanner only performs image search. Browseable lists live here:
 * 135 documented word signs, 86 syllable sounds, and the existing vocabulary.
 * None of these catalogues implies that the photograph is correctly classified.
 */
private enum class DictionarySection(val title: String) {
    WORD_SIGNS("Word meanings (135)"),
    SYLLABLES("Syllables (86)"),
    VOCABULARY("Vocabulary"),
}

@Composable
fun GlyphDictionaryHubScreen(
    entries: List<DictionaryEntry>,
    loading: Boolean,
    hasMore: Boolean,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSaveEntry: (DictionaryEntry) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(DictionarySection.WORD_SIGNS) }

    AppGradientBackground {
        Column(Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "Maya glyph dictionary",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Explore Maya glyph pictures, English meanings, syllable sounds and " +
                            "the existing vocabulary list. The image scanner is a separate page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ScrollableTabRow(
                selectedTabIndex = selected.ordinal,
                edgePadding = 12.dp,
            ) {
                DictionarySection.entries.forEach { section ->
                    Tab(
                        selected = selected == section,
                        onClick = { selected = section },
                        text = { Text(section.title, maxLines = 1) },
                    )
                }
            }
            when (selected) {
                DictionarySection.WORD_SIGNS ->
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        EnglishMeaningExploreCard(
                            bitmap = null, crop = GlyphCrop(), busy = false,
                            browseOnly = true,
                        )
                    }

                DictionarySection.SYLLABLES ->
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        GlyphCataloguePanel(
                            bitmap = null, crop = GlyphCrop(), busy = false,
                            browseOnly = true,
                        )
                    }

                DictionarySection.VOCABULARY ->
                    DictionaryScreen(
                        entries = entries,
                        loading = loading,
                        hasMore = hasMore,
                        onSearch = onSearch,
                        onLoadMore = onLoadMore,
                        onSaveEntry = onSaveEntry,
                    )
            }
        }
    }
}
