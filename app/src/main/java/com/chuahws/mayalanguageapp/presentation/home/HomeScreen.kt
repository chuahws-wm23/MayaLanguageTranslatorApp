package com.chuahws.mayalanguageapp.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.presentation.theme.MayaBlue
import com.chuahws.mayalanguageapp.presentation.theme.MayaGreen
import com.chuahws.mayalanguageapp.presentation.theme.MayaOrange
import com.chuahws.mayalanguageapp.presentation.theme.MayaPink
import com.chuahws.mayalanguageapp.presentation.theme.MayaPurple

@Composable
fun HomeScreen(
    displayName: String,
    onTextTranslation: () -> Unit,
    onImageTranslation: () -> Unit,
    onVoiceTranslation: () -> Unit,
    onHistory: () -> Unit,
    onAnalytics: () -> Unit,
    onFeedback: () -> Unit,
    onProfile: () -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Hello, ${displayName.ifBlank { "there" }}!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Let's learn and translate Maya.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            FeatureRow(
                left = Feature(
                    "Text Translation",
                    Icons.Rounded.Keyboard,
                    MayaPurple,
                    onTextTranslation,
                ),
                right = Feature(
                    "Image Translation",
                    Icons.Rounded.CameraAlt,
                    MayaPink,
                    onImageTranslation,
                ),
            )
        }

        item {
            FeatureRow(
                left = Feature(
                    "Voice Translation",
                    Icons.Rounded.Mic,
                    MayaBlue,
                    onVoiceTranslation,
                ),
                right = Feature(
                    "History",
                    Icons.Rounded.History,
                    MayaOrange,
                    onHistory,
                ),
            )
        }

        item {
            FeatureRow(
                left = Feature(
                    "Analytics",
                    Icons.Rounded.Analytics,
                    MayaGreen,
                    onAnalytics,
                ),
                right = Feature(
                    "Feedback",
                    Icons.Rounded.ChatBubble,
                    MayaPurple,
                    onFeedback,
                ),
            )
        }

        item {
            FeatureCard(
                feature = Feature(
                    "Profile",
                    Icons.Rounded.Person,
                    MayaPurple,
                    onProfile,
                ),
                modifier = Modifier.fillMaxWidth(),
                horizontal = true,
            )
        }
    }
}

private data class Feature(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit,
)

@Composable
private fun FeatureRow(
    left: Feature,
    right: Feature,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeatureCard(
            feature = left,
            modifier = Modifier.weight(1f),
        )
        FeatureCard(
            feature = right,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FeatureCard(
    feature: Feature,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    Card(
        modifier = modifier
            .height(if (horizontal) 82.dp else 120.dp)
            .clickable(onClick = feature.onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FeatureIcon(feature)
                Text(
                    text = feature.title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FeatureIcon(feature)
                Text(
                    text = feature.title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun FeatureIcon(feature: Feature) {
    Surface(
        color = feature.color.copy(alpha = 0.13f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(
            imageVector = feature.icon,
            contentDescription = null,
            tint = feature.color,
            modifier = Modifier.padding(11.dp),
        )
    }
}
