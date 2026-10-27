package com.chuahws.mayalanguageapp.presentation.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.model.UsageAnalytics
import com.chuahws.mayalanguageapp.presentation.components.AccentIcon
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.theme.MayaBlue
import com.chuahws.mayalanguageapp.presentation.theme.MayaGreen
import com.chuahws.mayalanguageapp.presentation.theme.MayaOrange
import com.chuahws.mayalanguageapp.presentation.theme.MayaPurple
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsScreen(
    analytics: UsageAnalytics,
    history: List<TranslationRecord>,
) {
    val weeklyActivity = rememberWeeklyActivity(history)
    val translationsThisWeek =
        weeklyActivity.sumOf { it.count }
    val mostUsedType = listOf(
        "Text" to analytics.textTranslations,
        "Image" to analytics.imageTranslations,
        "Voice" to analytics.voiceTranslations,
    ).maxByOrNull { it.second }
        ?.takeIf { it.second > 0 }
        ?.first
        ?: "None yet"
    AppGradientBackground {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        start = 18.dp,
                        top = 18.dp,
                        end = 18.dp,
                        bottom = 112.dp,
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentIcon(
                        icon = Icons.Rounded.Analytics,
                        color = MayaPurple,
                    )
                    Column {
                        Text(
                            text = "Your activity",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "See how you’ve been using the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryCard(
                    value = analytics.totalTranslations.toString(),
                    label = "Translations",
                    icon = Icons.Rounded.Keyboard,
                    color = MayaPurple,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    value = analytics.savedWords.toString(),
                    label = "Saved words",
                    icon = Icons.Rounded.Bookmark,
                    color = MayaOrange,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    value = translationsThisWeek.toString(),
                    label = "Last 7 days",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    value = mostUsedType,
                    label = "Most used",
                    modifier = Modifier.weight(1f),
                )
            }

            WeeklyActivityCard(
                days = weeklyActivity,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "How you translate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    TranslationDonut(analytics)

                    LegendRow(
                        "Text",
                        analytics.textTranslations,
                        MayaBlue,
                        Icons.Rounded.Keyboard,
                    )
                    LegendRow(
                        "Image",
                        analytics.imageTranslations,
                        MayaGreen,
                        Icons.Rounded.CameraAlt,
                    )
                    LegendRow(
                        "Voice",
                        analytics.voiceTranslations,
                        MayaOrange,
                        Icons.Rounded.Mic,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AccentIcon(
                icon = icon,
                color = color,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TranslationDonut(
    analytics: UsageAnalytics,
) {
    val values = listOf(
        analytics.textTranslations.toFloat(),
        analytics.imageTranslations.toFloat(),
        analytics.voiceTranslations.toFloat(),
    )
    val colors = listOf(MayaBlue, MayaGreen, MayaOrange)
    val rawTotal = values.sum()
    val total = rawTotal.coerceAtLeast(1f)
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
        ) {
            val diameter = size.minDimension * 0.72f
            val topLeft = Offset(
                (size.width - diameter) / 2,
                (size.height - diameter) / 2,
            )
            val arcSize = Size(diameter, diameter)
            var start = -90f

            values.forEachIndexed { index, value ->
                val sweep = if (rawTotal == 0f) {
                    if (index == 0) 360f else 0f
                } else {
                    360f * (value / total)
                }

                drawArc(
                    color = if (rawTotal == 0f) {
                        emptyColor
                    } else {
                        colors[index]
                    },
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = 28.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
                start += sweep
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = analytics.totalTranslations.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendRow(
    label: String,
    value: Long,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentIcon(
            icon = icon,
            color = color,
            modifier = Modifier.size(38.dp),
        )
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        Text(
            text = value.toString(),
            fontWeight = FontWeight.SemiBold,
        )
    }
}


private data class DailyActivity(
    val label: String,
    val count: Int,
)

@Composable
private fun rememberWeeklyActivity(
    history: List<TranslationRecord>,
): List<DailyActivity> {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStart = calendar.timeInMillis
    val dayMillis = 24L * 60L * 60L * 1000L
    val formatter = SimpleDateFormat(
        "EEE",
        Locale.getDefault(),
    )

    return (6 downTo 0).map { daysAgo ->
        val start = todayStart - daysAgo * dayMillis
        val end = start + dayMillis
        DailyActivity(
            label = formatter.format(Date(start)),
            count = history.count {
                it.createdAtMillis in start until end
            },
        )
    }
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeeklyActivityCard(
    days: List<DailyActivity>,
) {
    val maximum = days.maxOfOrNull { it.count }
        ?.coerceAtLeast(1)
        ?: 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Last 7 days",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Translations saved in your history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            days.forEach { day ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = day.label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.size(
                            width = 38.dp,
                            height = 20.dp,
                        ),
                    )
                    LinearProgressIndicator(
                        progress = {
                            day.count.toFloat() /
                                maximum.toFloat()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = day.count.toString(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
