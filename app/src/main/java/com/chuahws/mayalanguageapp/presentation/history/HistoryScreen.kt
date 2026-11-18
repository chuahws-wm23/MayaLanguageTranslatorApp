package com.chuahws.mayalanguageapp.presentation.history

import com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.chuahws.mayalanguageapp.domain.service.ListPaging
import com.chuahws.mayalanguageapp.presentation.components.ListPaginationControls
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.GlyphScanRecords
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.GlyphScanThumbnail
import com.chuahws.mayalanguageapp.presentation.components.GlyphScanDetailsDialog
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    history: List<TranslationRecord>,
    onDelete: (TranslationRecord) -> Unit,
    savedGlyphIds: Set<String> = emptySet(),
    onToggleGlyphFavorite: (TranslationRecord) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable(query) { mutableIntStateOf(1) }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val scans = remember(history) { history.filter { it.glyphScanRecord }.map { scan -> scan.copy(glyphCandidates = GlyphDisplayPolicy.visible(scan.glyphCandidates)) }.sortedByDescending { it.createdAtMillis } }
    val filtered = remember(scans, query) {
        val q = query.trim()
        scans.filter { scan ->
            q.isEmpty() || scan.glyphCandidates.any {
                it.englishGloss.contains(q,true) || it.reading.contains(q,true) || it.twkmSign.toString().contains(q)
            } || scan.translatedText.contains(q,true)
        }
    }
    val currentPage = ListPaging.safePage(page, filtered.size)
    val entries = ListPaging.pageItems(filtered, currentPage)
    LaunchedEffect(query, currentPage) {
        page = currentPage
        listState.scrollToItem(0)
    }
    scans.firstOrNull { it.translationId == selectedId }?.let { scan ->
        val saved = GlyphScanRecords.favoriteId(scan.userId,scan.translationId) in savedGlyphIds
        GlyphScanDetailsDialog(
            title = "Scan details", imageBase64 = scan.glyphImageBase64,
            candidates = scan.glyphCandidates, secondaryText = dateLabel(scan.createdAtMillis),
            scanKind = scan.glyphScanKind, onDismiss = { selectedId = null },
            actionLabel = if (saved) "Remove favourite" else "Save to favourites",
            onAction = { onToggleGlyphFavorite(scan) },
            onDelete = { selectedId = null; pendingDeleteId = scan.translationId },
        )
    }
    scans.firstOrNull { it.translationId == pendingDeleteId }?.let { scan ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete this scan?") },
            text = { Text("This removes the scan from your history.") },
            confirmButton = { TextButton(onClick = { onDelete(scan); pendingDeleteId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Keep scan") } },
        )
    }
    AppGradientBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("YOUR GLYPH JOURNAL", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text("Scan history", style = MaterialTheme.typography.headlineLarge)
                Text("Revisit the images and possible meanings you explored.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            stickyHeader {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        label = { Text("Search meaning, reading or sign number") },
                        shape = MaterialTheme.shapes.large)
                    ListPaginationControls(filtered.size, currentPage, { page = it })
                    Text(if (filtered.isEmpty()) "0 entries" else
                        "${ListPaging.firstVisibleIndex(currentPage,filtered.size)}–${ListPaging.lastVisibleIndex(currentPage,filtered.size)} of ${filtered.size} " +
                            if (filtered.size == 1) "entry" else "entries",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (filtered.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(if (scans.isEmpty()) "Your first discovery starts here" else "No matching scans",
                        style = MaterialTheme.typography.titleLarge)
                    Text(if (scans.isEmpty()) "Save a scan to keep its picture and possible meaning together."
                        else "Try another meaning or Maya reading.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(entries, key = { it.translationId }) { scan ->
                val best = scan.glyphCandidates.firstOrNull()
                val saved = GlyphScanRecords.favoriteId(scan.userId,scan.translationId) in savedGlyphIds
                Card(onClick = { selectedId = scan.translationId },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp)) {
                            GlyphScanThumbnail(scan.glyphImageBase64, side = 100.dp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (scan.glyphRegionCount > 1) "${scan.glyphRegionCount} scanned regions" else "SCANNED GLYPH",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(best?.englishGloss ?: "No match found",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            best?.let { Text("${it.reading} · TWKM #${it.twkmSign}",
                                style = MaterialTheme.typography.bodyMedium) }
                            Text(if (best != null && scan.glyphRegionCount > 1) "First candidate · tap for all saved results"
                                else if (best != null) "Possible meaning · unconfirmed" else "Try a clearer photo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateLabel(scan.createdAtMillis), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (saved) Icon(Icons.Rounded.Bookmark, contentDescription = "Saved to favourites",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun dateLabel(millis: Long): String =
    if (millis > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(millis))
    else "Date unavailable"
