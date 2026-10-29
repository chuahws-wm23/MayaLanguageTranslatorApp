package com.chuahws.mayalanguageapp.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.R

@Composable
fun AuthHeader(
    title: String,
    subtitle: String,
) {
    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(
                R.drawable.maya_translate_logo
            ),
            contentDescription =
                "Maya Translate logo",
            modifier = Modifier.size(88.dp),
        )

        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text = "Maya",
                style =
                    MaterialTheme.typography
                        .headlineSmall,
                color =
                    MaterialTheme.colorScheme
                        .primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = " Translate",
                style =
                    MaterialTheme.typography
                        .headlineSmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurface,
                fontWeight =
                    FontWeight.SemiBold,
            )
        }

        Text(
            text = title,
            style =
                MaterialTheme.typography
                    .headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = subtitle,
            style =
                MaterialTheme.typography
                    .bodyLarge,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )
    }
}
