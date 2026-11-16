package com.chuahws.mayalanguageapp.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.GlyphScanRecords
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.GlyphScanThumbnail

@Composable
fun HistoryScreen(
    history: List<TranslationRecord>,
    onDelete: (TranslationRecord) -> Unit,
    savedGlyphIds: Set<String> = emptySet(),
    onToggleGlyphFavorite: (TranslationRecord) -> Unit = {},
) {
    val scans = history.filter { it.glyphScanRecord && it.glyphImageBase64.isNotBlank() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    scans.firstOrNull { it.translationId == selectedId }?.let { scan ->
        val saved = GlyphScanRecords.favoriteId(scan.userId, scan.translationId) in savedGlyphIds
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text("Scan photo") },
            text = {
                GlyphScanThumbnail(scan.glyphImageBase64, modifier = Modifier.aspectRatio(1f))
            },
            confirmButton = {
                TextButton(onClick = {
                    onToggleGlyphFavorite(scan)
                    selectedId = null
                }) { Text(if (saved) "Remove favourite" else "Add to favourites") }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedId = null
                    pendingDeleteId = scan.translationId
                }) { Text("Delete") }
            },
        )
    }

    scans.firstOrNull { it.translationId == pendingDeleteId }?.let { scan ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete scan from History?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(scan)
                    pendingDeleteId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            },
        )
    }

    AppGradientBackground {
        if (scans.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No scans yet", style = MaterialTheme.typography.titleLarge)
                    Text("Photos from your glyph scans will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(scans, key = { it.translationId }) { scan ->
                    Card(
                        onClick = { selectedId = scan.translationId },
                        shape = MaterialTheme.shapes.large,
                    ) {
                        GlyphScanThumbnail(
                            scan.glyphImageBase64,
                            modifier = Modifier.aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}
