package com.chuahws.mayalanguageapp.presentation.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.model.UsageAnalytics
import com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.GlyphScanThumbnail

@Composable
internal fun ReportsScreen(history: List<TranslationRecord>, analytics: UsageAnalytics, loading: Boolean, startWithActivity: Boolean = false) {
    val scans = remember(history) {
        history.filter { it.glyphScanRecord }.sortedByDescending { it.createdAtMillis }
    }
    var activityMode by rememberSaveable { mutableStateOf(startWithActivity) }
    var selection by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var preview by rememberSaveable { mutableStateOf(false) }
    val selected = scans.filter { it.translationId in selection }
    val activity = listOf(
        "All recorded lookups: ${analytics.totalTranslations}",
        "Image lookups: ${analytics.imageTranslations}",
        "Saved items: ${analytics.savedWords}",
        "Glyph scans in available history: ${scans.size}",
        "Scans with a suggested reference in available history: ${scans.count { GlyphDisplayPolicy.visible(it.glyphCandidates).isNotEmpty() }}",
        "Activity totals reflect the account. Scan breakdown covers the History entries available in the app.",
    )
    if (preview && (activityMode || selected.isNotEmpty())) {
        ReportPreview(selected, activity = activity.takeIf { activityMode }) { preview = false }
    }
    AppGradientBackground {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Create a PDF", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!activityMode, { activityMode = false }, { Text("Scan history") })
                FilterChip(activityMode, { activityMode = true }, { Text("Overall activity") })
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (activityMode) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                activity.forEach { Text(it) }
                            }
                        }
                    }
                } else {
                    item {
                        if (scans.isEmpty()) Text("Saved scans will appear here after you scan a glyph.")
                        else {
                            Text("Choose which saved scans to include.")
                            TextButton(onClick = {
                                selection = if (selected.size == scans.size) arrayListOf()
                                else ArrayList(scans.map { it.translationId })
                            }) { Text(if (selected.size == scans.size) "Clear selection" else "Select all available scans") }
                        }
                    }
                    items(scans, key = { it.translationId }) { scan ->
                        val checked = scan.translationId in selection
                        val best = GlyphDisplayPolicy.visible(scan.glyphCandidates).firstOrNull()
                        OutlinedCard(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) { mark ->
                            selection = ArrayList(selection.filterNot { it == scan.translationId }).apply {
                                if (mark) add(scan.translationId)
                            }
                        }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlyphScanThumbnail(scan.glyphImageBase64, side = 56.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(best?.reading ?: "Unidentified glyph", style = MaterialTheme.typography.titleSmall)
                                    Text(reportScanDate(scan), style = MaterialTheme.typography.bodySmall)
                                }
                                Checkbox(checked, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
            Button(onClick = { preview = true }, enabled = if (activityMode) !loading else selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (activityMode) "Preview activity PDF" else "Preview PDF · ${selected.size} selected")
            }
        }
    }
}
