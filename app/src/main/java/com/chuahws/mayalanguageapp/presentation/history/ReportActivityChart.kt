package com.chuahws.mayalanguageapp.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal data class ScanDay(val label: String, val date: String, val suggested: Int, val unmatched: Int) {
    val total: Int get() = suggested + unmatched
}

internal fun weeklyScanActivity(scans: List<TranslationRecord>, now: Long = System.currentTimeMillis()): List<ScanDay> {
    val start = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -6)
    }
    val shortDay = SimpleDateFormat("EEE", Locale.getDefault())
    val fullDay = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    return List(7) {
        val from = start.timeInMillis
        val date = start.time
        start.add(Calendar.DAY_OF_YEAR, 1)
        val end = start.timeInMillis
        val dayScans = scans.filter { it.glyphScanRecord && it.createdAtMillis in from until end && it.createdAtMillis <= now }
        val suggested = dayScans.count { GlyphDisplayPolicy.visible(it.glyphCandidates).isNotEmpty() }
        ScanDay(shortDay.format(date).take(2), fullDay.format(date), suggested, dayScans.size - suggested)
    }
}

@Composable
internal fun ReportActivityChart(days: List<ScanDay>) {
    if (days.isEmpty()) return
    var picked by remember(days) { mutableIntStateOf(days.indexOfLast { it.total > 0 }.takeIf { it >= 0 } ?: days.lastIndex) }
    val peak = days.maxOf { it.total }.coerceAtLeast(1)
    val suggestedColor = MaterialTheme.colorScheme.primary
    val unmatchedColor = MaterialTheme.colorScheme.tertiary
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Scan activity", style = MaterialTheme.typography.titleLarge)
            Text("Last 7 days · available History", style = MaterialTheme.typography.bodySmall)
            if (days.all { it.total == 0 }) {
                Text("No saved scans in this period.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { index, day ->
                    Column(
                        Modifier.weight(1f).semantics {
                            contentDescription = "${day.date}: ${day.total} scans, ${day.suggested} with a suggestion"
                        }.clickable { picked = index },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(day.total.toString(), style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.fillMaxWidth().height(76.dp).background(
                            MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp),
                        ), contentAlignment = Alignment.BottomCenter) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
                                Spacer(Modifier.fillMaxWidth().height(76.dp * (day.unmatched.toFloat() / peak)).background(unmatchedColor))
                                Spacer(Modifier.fillMaxWidth().height(76.dp * (day.suggested.toFloat() / peak)).background(suggestedColor))
                            }
                        }
                        Text(day.label, style = MaterialTheme.typography.labelSmall,
                            color = if (picked == index) suggestedColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartLegend("Suggested", suggestedColor)
                ChartLegend("No suggestion", unmatchedColor)
            }
            val day = days[picked]
            Text("${day.date} · ${day.suggested} suggested, ${day.unmatched} without a suggestion",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChartLegend(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ReportOverviewMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
