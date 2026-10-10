package com.chuahws.mayalanguageapp.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ResetPasswordScreen(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onResetPassword: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Reset Password",
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = "Enter the email address used for your account.",
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Registered email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
        )

        state.message?.let {
            Text(
                text = it,
                color = if (state.passwordResetSent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Button(
            onClick = onResetPassword,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text("Send reset email")
        }

        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        TextButton(onClick = onBackToLogin) {
            Text("Back to login")
        }
    }
}
