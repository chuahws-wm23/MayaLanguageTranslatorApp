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
    val days = remember(scans) { weeklyScanActivity(scans) }
    val activity = listOf(
        "All recorded lookups: ${analytics.totalTranslations}",
        "Image lookups: ${analytics.imageTranslations}",
        "Saved items: ${analytics.savedWords}",
        "Saved scans in the last 7 days: ${days.sumOf { it.total }}",
        "Glyph scans in available history: ${scans.size}",
        "Scans with a suggested reference in available history: ${scans.count { GlyphDisplayPolicy.visible(it.glyphCandidates).isNotEmpty() }}",
        "Activity totals reflect the account. Scan breakdown covers the History entries available in the app.",
    )
    if (preview && (activityMode || selected.isNotEmpty())) {
        ReportPreview(selected, activity = activity.takeIf { activityMode }, activityDays = days.takeIf { activityMode }.orEmpty()) { preview = false }
    }
    AppGradientBackground {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Research reports", style = MaterialTheme.typography.headlineSmall)
                    Text("Export selected glyph scans or an activity summary.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!activityMode, { activityMode = false }, { Text("Scan history") })
                FilterChip(activityMode, { activityMode = true }, { Text("Overall activity") })
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (activityMode) {
                    item {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportOverviewMetric(analytics.totalTranslations.toString(), "Lookups", Modifier.weight(1f).fillMaxHeight())
                            ReportOverviewMetric(analytics.imageTranslations.toString(), "Image lookups", Modifier.weight(1f).fillMaxHeight())
                            ReportOverviewMetric(analytics.savedWords.toString(), "Saved items", Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    item { ReportActivityChart(days) }
                    item { Text(activity.last(), style = MaterialTheme.typography.bodySmall) }
                } else {
                    item {
                        if (scans.isEmpty()) Text("Saved scans will appear here after you scan a glyph.")
                        else {
                            Text("Saved scans", style = MaterialTheme.typography.titleLarge)
                            Text("${selected.size} of ${scans.size} selected", style = MaterialTheme.typography.bodyMedium)
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
                                    Text(if (best == null) "No suggestion" else "TWKM #${best.twkmSign}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
