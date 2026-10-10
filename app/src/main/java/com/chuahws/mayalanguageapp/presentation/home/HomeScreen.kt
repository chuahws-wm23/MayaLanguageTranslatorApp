package com.chuahws.mayalanguageapp.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.AuthUser

@Composable
fun HomeScreen(
    user: AuthUser,
    onTextTranslation: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Welcome, ${user.displayName ?: user.email}",
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = "Iteration 1 currently provides account management and typed-text translation.",
        )

        Button(
            onClick = onTextTranslation,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Text Translation")
        }

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Log Out")
        }
    }
}
