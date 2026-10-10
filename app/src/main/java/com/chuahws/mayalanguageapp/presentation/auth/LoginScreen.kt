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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    state: AuthUiState,
    firebaseConfigured: Boolean,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Maya Language Translator",
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = "Yucatec Maya ↔ English",
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )

        if (!firebaseConfigured) {
            Text(
                text = "Development mode: Firebase is not connected yet.",
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
        )

        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Button(
            onClick = onLogin,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text("Log In")
        }

        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        TextButton(onClick = onForgotPassword) {
            Text("Forgot password?")
        }

        TextButton(onClick = onRegister) {
            Text("Create a new account")
        }
    }
}
