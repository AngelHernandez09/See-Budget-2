package com.seebudget.app.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.components.BrutalButton
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.AuthErrorMessage
import com.seebudget.app.presentation.viewmodel.AuthInfoMessage
import com.seebudget.app.presentation.viewmodel.AuthViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-07 — registro/login. No es una de las 11 pantallas del documento (que
 * no lista ninguna de login/registro, aunque RF-07 las requiere) — gap que
 * quedó identificado en el chat, pendiente de reflejar en el documento de
 * requerimientos.
 */
@Composable
fun LoginScreen(viewModel: AuthViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = LocalSeeBudgetColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("See Budget", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (uiState.isSignUp) {
                stringResource(Res.string.login_title_sign_up)
            } else {
                stringResource(Res.string.login_title_sign_in)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text(stringResource(Res.string.login_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(stringResource(Res.string.login_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        uiState.errorMessage?.let { error ->
            val message = when (error) {
                is AuthErrorMessage.Raw -> error.text
                AuthErrorMessage.AuthenticationFailed -> stringResource(Res.string.login_error_generic)
            }
            Spacer(Modifier.height(12.dp))
            Text(message, color = colors.accentError, style = MaterialTheme.typography.labelMedium)
        }
        uiState.infoMessage?.let { info ->
            val message = when (info) {
                AuthInfoMessage.SignUpConfirmationRequired -> stringResource(Res.string.login_info_confirm_email)
            }
            Spacer(Modifier.height(12.dp))
            Text(message, color = colors.accentPositive, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(24.dp))
        BrutalButton(
            onClick = viewModel::submit,
            enabled = !uiState.isLoading && uiState.email.isNotBlank() && uiState.password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    // "..." es un indicador visual de carga, no texto de idioma
                    // — no se traduce (ver tarea de migración de i18n).
                    uiState.isLoading -> "..."
                    uiState.isSignUp -> stringResource(Res.string.login_button_create_account)
                    else -> stringResource(Res.string.login_button_sign_in)
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = viewModel::toggleMode) {
            Text(
                if (uiState.isSignUp) {
                    stringResource(Res.string.login_toggle_to_sign_in)
                } else {
                    stringResource(Res.string.login_toggle_to_sign_up)
                },
            )
        }
    }
}
