package com.chuahws.mayalanguageapp.presentation.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
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
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.translation.EnglishMeaningExploreCard
import com.chuahws.mayalanguageapp.presentation.translation.GlyphCataloguePanel

private enum class DictionarySection(val title: String) {
    WORD_SIGNS("Word signs"),
    SYLLABLES("Syllables"),
}

@Composable
fun GlyphDictionaryHubScreen() {
    var selected by rememberSaveable { mutableStateOf(DictionarySection.WORD_SIGNS) }

    AppGradientBackground {
        Column(Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
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
                        "Browse word signs and syllables A–Z.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TabRow(
                selectedTabIndex = selected.ordinal,
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

            }
        }
    }
}
