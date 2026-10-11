package com.chuahws.mayalanguageapp.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.presentation.theme.MayaSize

@Composable
fun RegisterScreen(
    state: AuthUiState,
    onDisplayNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onRegister: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AuthHeader(
            title = "Create your account",
            subtitle = "Save translations, build a vocabulary list and track your activity.",
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChanged,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Name")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    focusManager.moveFocus(FocusDirection.Down)
                },
            ),
        )

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            label = {
                Text("Email")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    focusManager.moveFocus(FocusDirection.Down)
                },
            ),
        )

        PasswordField(
            value = state.password,
            onValueChange = onPasswordChanged,
            label = "Password",
            enabled = !state.loading,
            supportingText = "At least 6 characters",
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(
                onNext = {
                    focusManager.moveFocus(FocusDirection.Down)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        PasswordField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChanged,
            label = "Confirm password",
            enabled = !state.loading,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onRegister()
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                onRegister()
            },
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MayaSize.buttonHeight)
                .padding(top = 20.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Create account")
            }
        }

        TextButton(
            onClick = onBackToLogin,
            enabled = !state.loading,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Back to login")
        }
    }
}
