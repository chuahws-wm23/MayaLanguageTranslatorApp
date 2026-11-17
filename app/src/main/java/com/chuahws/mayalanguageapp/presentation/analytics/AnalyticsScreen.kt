package com.chuahws.mayalanguageapp.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.GlyphScanThumbnail
import com.chuahws.mayalanguageapp.presentation.learn.LearningContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsScreen(
    history: List<TranslationRecord>,
    userId: String,
    savedGlyphCount: Int,
    onScan: () -> Unit,
    onLearn: () -> Unit,
    onHistory: () -> Unit,
    onFavourites: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("maya_learning_$userId", 0)
    val completed = preferences.getStringSet("completed", emptySet()).orEmpty()
        .intersect(LearningContent.lessons.map { it.id }.toSet()).size
    val best = preferences.getInt("quiz_best", 0).coerceIn(0, LearningContent.questions.size)
    // Refresh dates on re-entry and when returning from background, even across midnight.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        now = System.currentTimeMillis()
    }
    val summary = remember(history, now) { glyphActivitySummary(history, now) }
    var weekOnly by rememberSaveable { mutableStateOf(true) }
    val recent = if (weekOnly) summary.scans.filter { it.createdAtMillis >= summary.days.first().startMillis }
        else summary.scans
    val dateFormat = remember { SimpleDateFormat("d MMM · h:mm a", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }

    AppGradientBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("activity-content"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF183B30), contentColor = Color(0xFFF6F1E6))) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("YOUR EXPLORER JOURNAL", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE4CB91))
                        Text("Small discoveries.\nGrowing understanding.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(if (summary.weeklyScans == 0) "Your next discovery starts with a glyph or a lesson."
                            else "${summary.weeklyScans} scans across ${summary.activeDays} active days in the last 7 days.")
                        Text("Explore at your own pace.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActivityMetric("${summary.scans.size}", "Glyph scans", Modifier.weight(1f).fillMaxHeight())
                    ActivityMetric("$savedGlyphCount", "Glyph favourites", Modifier.weight(1f).fillMaxHeight(), onFavourites)
                }
            }
            item {
                ActivityCard("Your week in glyphs", "Scans saved in History · last 7 days") {
                    val maximum = summary.days.maxOf { it.count }.coerceAtLeast(1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        summary.days.forEachIndexed { index, day ->
                            val label = dayFormat.format(Date(day.startMillis))
                            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {
                                contentDescription = "$label: ${day.count} scans${if (index == 6) ", today" else ""}"
                            }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${day.count}", style = MaterialTheme.typography.labelMedium)
                                Box(Modifier.height(88.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth().height(if (day.count == 0) 4.dp else (12 + 76f * day.count / maximum).dp)
                                        .background(if (index == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)))
                                }
                                Text(label.take(1), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Text("${summary.activeDays} of 7 days active · Today is the rightmost column", style = MaterialTheme.typography.bodySmall)
                    if (summary.weeklyScans == 0) Text("No scans this week yet. Try a clear glyph drawing to begin.")
                }
            }
            item {
                ActivityCard("Your learning journey", "Lesson completion and your best practice result") {
                    Text("$completed of ${LearningContent.lessons.size} lessons completed", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { completed.toFloat() / LearningContent.lessons.size }, modifier = Modifier.fillMaxWidth())
                    Text("Best quiz: $best/${LearningContent.questions.size}", style = MaterialTheme.typography.titleMedium)
                    Text("Learning progress is saved on this device for your account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onLearn, modifier = Modifier.fillMaxWidth()) {
                        Text(if (completed == LearningContent.lessons.size) "Review lessons and practise" else "Continue learning")
                    }
                }
            }
            item {
                ActivityCard("Exploration milestones", "Based on the glyph scans currently in your history") {
                    Milestone("First discovery", "Save your first glyph scan", summary.scans.isNotEmpty())
                    Milestone("A week of curiosity", "Scan on 3 different days within the last 7", summary.activeDays >= 3)
                    Milestone("Beyond a single sign", "Try a scan with multiple detected regions", summary.multiScans > 0)
                    Milestone("Build your collection", "Favourite a glyph scan", savedGlyphCount > 0)
                }
            }
            item {
                Text("Recent explorations", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = weekOnly, onClick = { weekOnly = true }, label = { Text("Last 7 days") })
                    FilterChip(selected = !weekOnly, onClick = { weekOnly = false }, label = { Text("All history") })
                }
            }
            if (recent.isEmpty()) item {
                ActivityCard("A fresh page", if (weekOnly) "No glyph scans in the last 7 days. Explore a sign or revisit your earlier history." else "Your glyph scans will appear here after you explore a photo.") {
                    Button(onClick = onScan) { Text("Scan a glyph") }
                }
            }
            items(recent.take(3)) { scan ->
                Card(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlyphScanThumbnail(scan.glyphImageBase64, side = 60.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(scan.glyphCandidates.firstOrNull()?.reading?.takeIf { it.isNotBlank() } ?: "Glyph exploration", style = MaterialTheme.typography.titleMedium)
                            Text(dateFormat.format(Date(scan.createdAtMillis)), style = MaterialTheme.typography.bodySmall)
                            Text(if (scan.glyphRegionCount > 1) "${scan.glyphRegionCount} detected regions · reference suggestions" else "Reference suggestions · unverified", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Open scan history") }
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Start a new exploration") }
            }
            item { Text("Scan totals reflect your current glyph history; deleting a scan updates these figures. Activity measures exploration, not recognition accuracy or reading proficiency.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ActivityMetric(value: String, label: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (onClick == null) Card(modifier = modifier) { content() }
    else Card(onClick = onClick, modifier = modifier) { content() }
}

@Composable
private fun ActivityCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun Milestone(title: String, description: String, achieved: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${if (achieved) "✓" else "○"} $title", style = MaterialTheme.typography.titleMedium)
        Text("${if (achieved) "Reached" else "To explore"} · $description", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
