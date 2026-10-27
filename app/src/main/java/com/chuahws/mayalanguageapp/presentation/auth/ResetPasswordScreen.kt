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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.theme.MayaSize

@Composable
fun ResetPasswordScreen(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onResetPassword: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    AppGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 22.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AuthHeader(
                title = "Reset password",
                subtitle = "Enter your account email and we’ll send a secure reset link.",
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChanged,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading,
                        label = {
                            Text("Email address")
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Email,
                                contentDescription = null,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                onResetPassword()
                            },
                        ),
                    )

                    state.message?.let {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor =
                                    if (state.passwordResetSent) {
                                        MaterialTheme.colorScheme
                                            .secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme
                                            .errorContainer
                                    },
                            ),
                        ) {
                            Text(
                                text = it,
                                color =
                                    if (state.passwordResetSent) {
                                        MaterialTheme.colorScheme
                                            .onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme
                                            .onErrorContainer
                                    },
                                style =
                                    MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onResetPassword()
                        },
                        enabled = !state.loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MayaSize.buttonHeight),
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                                color =
                                    MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                "Send reset link",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onBackToLogin,
                enabled = !state.loading,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Back to sign in")
            }
        }
    }
}
