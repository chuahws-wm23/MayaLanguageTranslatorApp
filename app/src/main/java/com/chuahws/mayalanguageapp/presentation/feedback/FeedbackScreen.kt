package com.chuahws.mayalanguageapp.presentation.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.presentation.components.AccentIcon
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.theme.MayaPink

private const val MAX_FEEDBACK_LENGTH = 500

@Composable
fun FeedbackScreen(
    email: String,
    submissionVersion: Long,
    submitting: Boolean,
    onSubmit: (Int, String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var rating by rememberSaveable {
        mutableIntStateOf(0)
    }
    var comment by rememberSaveable {
        mutableStateOf("")
    }

    LaunchedEffect(submissionVersion) {
        if (submissionVersion > 0) {
            rating = 0
            comment = ""
            focusManager.clearFocus()
        }
    }

    val ratingText = when (rating) {
        1 -> "Very poor"
        2 -> "Poor"
        3 -> "Okay"
        4 -> "Very good"
        5 -> "Excellent"
        else -> "Tap a star to rate"
    }

    val canSubmit =
        rating in 1..5 &&
            comment.trim().length >= 4

    AppGradientBackground {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(
                    PaddingValues(
                        start = 18.dp,
                        top = 18.dp,
                        end = 18.dp,
                        bottom = 112.dp,
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentIcon(
                        icon = Icons.Rounded.ChatBubble,
                        color = MayaPink,
                    )
                    Text(
                        text = "How was your experience?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Your feedback helps improve Maya Translate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        (1..5).forEach { value ->
                            IconButton(
                                onClick = {
                                    rating = value
                                },
                                enabled = !submitting,
                            ) {
                                Icon(
                                    imageVector =
                                        if (value <= rating) {
                                            Icons.Rounded.Star
                                        } else {
                                            Icons.Rounded.StarBorder
                                        },
                                    contentDescription =
                                        "$value star rating",
                                    tint =
                                        if (value <= rating) {
                                            MayaPink
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                )
                            }
                        }
                    }

                    Text(
                        text = ratingText,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = comment,
                        onValueChange = {
                            if (it.length <= MAX_FEEDBACK_LENGTH) {
                                comment = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !submitting,
                        label = {
                            Text("Write your feedback")
                        },
                        placeholder = {
                            Text(
                                "Tell us what worked well or what should be improved."
                            )
                        },
                        supportingText = {
                            Text(
                                "${comment.length}/$MAX_FEEDBACK_LENGTH"
                            )
                        },
                        minLines = 6,
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Email")
                        },
                        singleLine = true,
                    )
                }
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSubmit(rating, comment)
                },
                enabled = canSubmit && !submitting,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 15.dp),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = "Submit feedback",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
