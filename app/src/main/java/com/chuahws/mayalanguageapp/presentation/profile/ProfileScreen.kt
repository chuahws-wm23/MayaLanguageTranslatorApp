package com.chuahws.mayalanguageapp.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.UsageAnalytics
import com.chuahws.mayalanguageapp.domain.model.UserProfile

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    email: String,
    analytics: UsageAnalytics,
    onUpdateName: (String) -> Unit,
    onSubmitFeedback: (Int, String) -> Unit,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
) {
    var name by rememberSaveable(profile?.displayName) {
        mutableStateOf(profile?.displayName.orEmpty())
    }
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = profile?.displayName
                        ?.ifBlank { email }
                        ?: email,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = email,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Display name") },
                    singleLine = true,
                )

                Button(
                    onClick = { onUpdateName(name) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save changes")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("${analytics.totalTranslations} translations")
                Text("${analytics.savedWords} saved words")
                Text("${analytics.imageTranslations} image scans")
                Text("${analytics.voiceTranslations} voice translations")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Send feedback",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..5).forEach { value ->
                        FilterChip(
                            selected = rating == value,
                            onClick = { rating = value },
                            label = { Text(value.toString()) },
                        )
                    }
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Comment") },
                    minLines = 3,
                )

                Button(
                    onClick = {
                        onSubmitFeedback(rating, comment)
                        if (rating in 1..5 && comment.trim().length >= 4) {
                            rating = 0
                            comment = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Submit feedback")
                }
            }
        }

        if (profile?.role == "admin") {
            OutlinedButton(
                onClick = onOpenAdmin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Rounded.AdminPanelSettings,
                    contentDescription = null,
                )
                Text(
                    text = "Admin tools",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Logout, contentDescription = null)
            Text(
                text = "Log out",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
