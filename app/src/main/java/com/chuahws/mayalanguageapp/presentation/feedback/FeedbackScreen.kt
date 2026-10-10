package com.chuahws.mayalanguageapp.presentation.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FeedbackScreen(
    email: String,
    onSubmit: (Int, String) -> Unit,
) {
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }

    val ratingText = when (rating) {
        1 -> "Very poor"
        2 -> "Poor"
        3 -> "Okay"
        4 -> "Very good"
        5 -> "Excellent"
        else -> "Tap a star to rate"
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "How was your experience with Maya Language App?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            horizontalArrangement = Arrangement.Center,
        ) {
            (1..5).forEach { value ->
                IconButton(onClick = { rating = value }) {
                    Icon(
                        imageVector = if (value <= rating) {
                            Icons.Rounded.Star
                        } else {
                            Icons.Rounded.StarBorder
                        },
                        contentDescription = "$value stars",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Text(
            text = ratingText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Write your feedback") },
            placeholder = {
                Text("Tell us what worked well or what should be improved.")
            },
            minLines = 5,
        )

        OutlinedTextField(
            value = email,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
        )

        Button(
            onClick = {
                onSubmit(rating, comment)
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
