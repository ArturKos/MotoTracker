package com.mototracker.ui.screens.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.ui.theme.JetBrainsMonoFamily
import com.mototracker.ui.theme.MotoTracker

/**
 * Register screen — thin ViewModel wrapper that delegates to [RegisterContent].
 *
 * Navigation is driven by one-shot [RegisterEvent]s from [viewModel.events]:
 * [RegisterEvent.NavigateToMain] calls [onRegistered];
 * [RegisterEvent.NavigateBackToLogin] calls [onBackToLogin].
 *
 * @param onRegistered   Called after successful registration (authed = true when auto-logged-in).
 * @param onBackToLogin  Called when the user taps the "Back to login" link.
 * @param modifier       Applied to the root container.
 * @param viewModel      Hilt-injected [RegisterViewModel].
 */
@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RegisterEvent.NavigateToMain -> onRegistered()
                RegisterEvent.NavigateBackToLogin -> onBackToLogin()
            }
        }
    }

    RegisterContent(
        uiState = uiState,
        onServerAddress = viewModel::updateServerAddress,
        onEmail = viewModel::updateEmail,
        onPassword = viewModel::updatePassword,
        onConfirm = viewModel::updateConfirm,
        onRegister = viewModel::register,
        onBackToLogin = viewModel::navigateBackToLogin,
        modifier = modifier,
    )
}

/**
 * Pure renderer for the Register screen.
 * Extracted for unit testing — no ViewModels or side-effects.
 *
 * @param uiState         Snapshot of all form field values and loading/error state.
 * @param onServerAddress Called when the server-address field changes.
 * @param onEmail         Called when the e-mail field changes.
 * @param onPassword      Called when the password field changes.
 * @param onConfirm       Called when the confirm-password field changes.
 * @param onRegister      Called when the user taps "Create account".
 * @param onBackToLogin   Called when the user taps the "Back to login" link.
 * @param modifier        Applied to the root container.
 */
@Composable
fun RegisterContent(
    uiState: RegisterUiState,
    onServerAddress: (String) -> Unit = {},
    onEmail: (String) -> Unit = {},
    onPassword: (String) -> Unit = {},
    onConfirm: (String) -> Unit = {},
    onRegister: () -> Unit = {},
    onBackToLogin: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .background(MotoTracker.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
    ) {
        Text(
            text = stringResource(R.string.register_title).uppercase(),
            style = MotoTracker.typography.screenTitle,
            color = MotoTracker.colors.text,
        )

        Spacer(Modifier.height(32.dp))

        RegisterFieldLabel(stringResource(R.string.label_server_address))
        OutlinedTextField(
            value = uiState.serverAddress,
            onValueChange = onServerAddress,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MotoTracker.typography.body.copy(fontFamily = JetBrainsMonoFamily),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = registerFieldColors(),
        )

        Spacer(Modifier.height(12.dp))

        RegisterFieldLabel(stringResource(R.string.register_label_email))
        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmail,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MotoTracker.typography.body,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.register_hint_email),
                    style = MotoTracker.typography.body,
                    color = MotoTracker.colors.dim,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = registerFieldColors(),
        )

        Spacer(Modifier.height(12.dp))

        RegisterFieldLabel(stringResource(R.string.register_label_password))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPassword,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MotoTracker.typography.body,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.register_hint_password),
                    style = MotoTracker.typography.body,
                    color = MotoTracker.colors.dim,
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = registerFieldColors(),
        )

        Spacer(Modifier.height(12.dp))

        RegisterFieldLabel(stringResource(R.string.register_label_confirm))
        OutlinedTextField(
            value = uiState.confirm,
            onValueChange = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MotoTracker.typography.body,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.register_hint_confirm),
                    style = MotoTracker.typography.body,
                    color = MotoTracker.colors.dim,
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = registerFieldColors(),
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onRegister,
            enabled = uiState.canSubmit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MotoTracker.colors.accent,
                contentColor = MotoTracker.colors.onAccent,
                disabledContainerColor = MotoTracker.colors.panel,
                disabledContentColor = MotoTracker.colors.dim,
            ),
        ) {
            if (uiState.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MotoTracker.colors.onAccent,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(R.string.register_submit).uppercase(),
                    style = MotoTracker.typography.label,
                )
            }
        }

        val errorRes = uiState.errorMessage
        if (errorRes != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(errorRes),
                style = MotoTracker.typography.bodySmall,
                color = MotoTracker.colors.accent2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onBackToLogin) {
            Text(
                text = stringResource(R.string.register_back_to_login),
                style = MotoTracker.typography.bodySmall,
                color = MotoTracker.colors.dim,
            )
        }
    }
}

@Composable
private fun RegisterFieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MotoTracker.typography.label,
        color = MotoTracker.colors.dim,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    )
}

@Composable
private fun registerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MotoTracker.colors.text,
    unfocusedTextColor = MotoTracker.colors.text,
    focusedBorderColor = MotoTracker.colors.accent,
    unfocusedBorderColor = MotoTracker.colors.line,
    cursorColor = MotoTracker.colors.accent,
)
