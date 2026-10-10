package com.chuahws.mayalanguageapp.presentation.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.UsageAnalytics
import com.chuahws.mayalanguageapp.presentation.theme.MayaBlue
import com.chuahws.mayalanguageapp.presentation.theme.MayaGreen
import com.chuahws.mayalanguageapp.presentation.theme.MayaOrange
import com.chuahws.mayalanguageapp.presentation.theme.MayaPurple

@Composable
fun AnalyticsScreen(
    analytics: UsageAnalytics,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(
                value = analytics.totalTranslations.toString(),
                label = "Total translations",
                color = MayaPurple,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                value = analytics.savedWords.toString(),
                label = "Saved words",
                color = MayaOrange,
                modifier = Modifier.weight(1f),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Translations by type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                TranslationDonut(analytics)

                LegendRow("Text", analytics.textTranslations, MayaBlue)
                LegendRow("Image", analytics.imageTranslations, MayaGreen)
                LegendRow("Voice", analytics.voiceTranslations, MayaOrange)
            }
        }
    }
}

@Composable
private fun SummaryCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
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
    val total = values.sum().coerceAtLeast(1f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        val diameter = size.minDimension * 0.72f
        val topLeft = Offset(
            (size.width - diameter) / 2,
            (size.height - diameter) / 2,
        )
        val arcSize = Size(diameter, diameter)
        var start = -90f

        values.forEachIndexed { index, value ->
            val sweep = if (values.sum() == 0f) {
                if (index == 0) 360f else 0f
            } else {
                360f * (value / total)
            }

            drawArc(
                color = if (values.sum() == 0f) {
                    MaterialTheme.colorScheme.surfaceVariant
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
                    cap = StrokeCap.Butt,
                ),
            )
            start += sweep
        }
    }
}

@Composable
private fun LegendRow(
    label: String,
    value: Long,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(modifier = Modifier.height(14.dp)) {
                drawCircle(
                    color = color,
                    radius = 6.dp.toPx(),
                    center = Offset(6.dp.toPx(), center.y),
                )
            }
            Text(label)
        }
        Text(
            text = value.toString(),
            fontWeight = FontWeight.SemiBold,
        )
    }
}
