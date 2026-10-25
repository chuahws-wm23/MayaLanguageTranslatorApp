package com.chuahws.mayalanguageapp.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.presentation.components.AccentIcon
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.SectionLabel
import com.chuahws.mayalanguageapp.presentation.theme.MayaBlue
import com.chuahws.mayalanguageapp.presentation.theme.MayaCyan
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
    onSaved: () -> Unit,
    onHistory: () -> Unit,
    onAnalytics: () -> Unit,
    onFeedback: () -> Unit,
    onDictionary: () -> Unit,
    onProfile: () -> Unit,
) {
    AppGradientBackground {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 18.dp,
                end = 18.dp,
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                GreetingHeader(
                    displayName = displayName,
                    onProfile = onProfile,
                )
            }

            item {
                HeroCard(
                    onClick = onTextTranslation,
                )
            }

            item {
                SectionLabel(
                    title = "Quick access",
                    subtitle = "Translate · Learn · Review",
                )
            }

            item {
                FeatureRow(
                    left = Feature(
                        title = "Text Translation",
                        subtitle = "Type or paste text",
                        icon = Icons.Rounded.Keyboard,
                        color = MayaPurple,
                        onClick = onTextTranslation,
                    ),
                    right = Feature(
                        title = "Image Translation",
                        subtitle = "Scan from a photo",
                        icon = Icons.Rounded.CameraAlt,
                        color = MayaPink,
                        onClick = onImageTranslation,
                    ),
                )
            }

            item {
                FeatureRow(
                    left = Feature(
                        title = "Voice Translation",
                        subtitle = "Speak in English",
                        icon = Icons.Rounded.Mic,
                        color = MayaBlue,
                        onClick = onVoiceTranslation,
                    ),
                    right = Feature(
                        title = "Saved Vocabulary",
                        subtitle = "Your word collection",
                        icon = Icons.Rounded.Bookmark,
                        color = MayaOrange,
                        onClick = onSaved,
                    ),
                )
            }

            item {
                FeatureRow(
                    left = Feature(
                        title = "History",
                        subtitle = "Recent translations",
                        icon = Icons.Rounded.History,
                        color = MayaCyan,
                        onClick = onHistory,
                    ),
                    right = Feature(
                        title = "Analytics",
                        subtitle = "Track your activity",
                        icon = Icons.Rounded.Analytics,
                        color = MayaGreen,
                        onClick = onAnalytics,
                    ),
                )
            }

            item {
                FeatureRow(
                    left = Feature(
                        title = "Feedback",
                        subtitle = "Share your thoughts",
                        icon = Icons.Rounded.ChatBubble,
                        color = MayaPink,
                        onClick = onFeedback,
                    ),
                    right = Feature(
                        title = "Dictionary",
                        subtitle = "Browse Maya vocabulary",
                        icon = Icons.Rounded.MenuBook,
                        color = MayaPurple,
                        onClick = onDictionary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun GreetingHeader(
    displayName: String,
    onProfile: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Good to see you!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = displayName.ifBlank { "Maya learner" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Surface(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onProfile),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = "Open profile",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    onClick: () -> Unit,
) {
    val gradient = Brush.horizontalGradient(
        listOf(
            Color(0xFF6C3BEF),
            Color(0xFF8B4FE8),
            Color(0xFF5035C8),
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 158.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 5.dp,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = CircleShape,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 6.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Yucatec Maya",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Text(
                    text = "Explore the language,\none word at a time.",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "Translate · Learn · Discover",
                    color = Color.White.copy(alpha = 0.83f),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Start translating",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private data class Feature(
    val title: String,
    val subtitle: String,
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
) {
    Card(
        modifier = modifier
            .heightIn(min = 134.dp)
            .clickable(onClick = feature.onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccentIcon(
                icon = feature.icon,
                color = feature.color,
            )

            Text(
                text = feature.title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )

            Text(
                text = feature.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
