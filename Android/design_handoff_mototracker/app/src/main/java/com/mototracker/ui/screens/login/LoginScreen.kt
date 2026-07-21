package com.mototracker.ui.screens.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.ui.theme.BarlowSemiCondensedFamily
import com.mototracker.ui.theme.JetBrainsMonoFamily
import com.mototracker.ui.theme.MotoTracker

/**
 * Login screen — thin ViewModel wrapper that delegates to [LoginContent].
 *
 * Navigation is driven by one-shot [LoginEvent]s from [viewModel.events]:
 * [LoginEvent.NavigateToMain] with `authed = true` calls [onSignedIn].
 *
 * @param onSignedIn      Called after a successful sign-in flow.
 * @param onGuest         Called when the user chooses to continue without an account.
 * @param onCreateAccount Called when the user taps "Create account" to navigate to the register screen.
 * @param sessionExpired  When `true`, an inline notice informs the user their previous session
 *                        has expired and they must sign in again (B22). 🔬 on-device UI.
 * @param modifier        Applied to the root container.
 * @param viewModel       Hilt-injected [LoginViewModel].
 */
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onGuest: () -> Unit,
    onCreateAccount: () -> Unit = {},
    sessionExpired: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.NavigateToMain -> if (event.authed) onSignedIn() else onGuest()
            }
        }
    }

    LoginContent(
        uiState = uiState,
        sessionExpired = sessionExpired,
        onServerAddress = viewModel::updateServerAddress,
        onEmail = viewModel::updateEmail,
        onPassword = viewModel::updatePassword,
        onSignIn = viewModel::signIn,
        onGuest = viewModel::continueAsGuest,
        onCreateAccount = onCreateAccount,
        modifier = modifier,
    )
}

/**
 * Pure renderer for the Login screen: logo, form fields, sign-in and guest buttons.
 * Extracted for Paparazzi screenshot testing — no ViewModels or side-effects.
 *
 * @param uiState          Snapshot of all form field values and loading/error state.
 * @param sessionExpired   When `true`, shows an inline session-expired notice above the form (B22). 🔬
 * @param onServerAddress  Called when the server-address field changes.
 * @param onEmail          Called when the e-mail field changes.
 * @param onPassword       Called when the password field changes.
 * @param onSignIn         Called when the user taps "Sign in".
 * @param onGuest          Called when the user taps "Continue as guest".
 * @param onCreateAccount  Called when the user taps "Create account".
 * @param modifier         Applied to the root container.
 */
@Composable
fun LoginContent(
    uiState: LoginUiState,
    sessionExpired: Boolean = false,
    onServerAddress: (String) -> Unit = {},
    onEmail: (String) -> Unit = {},
    onPassword: (String) -> Unit = {},
    onSignIn: () -> Unit = {},
    onGuest: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
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
        // 92×92 logo sygnet card
        Card(
            modifier = Modifier.size(92.dp),
            colors = CardDefaults.cardColors(containerColor = MotoTracker.colors.panel),
            shape = MaterialTheme.shapes.medium,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    text = "M",
                    fontSize = 52.sp,
                    fontFamily = BarlowSemiCondensedFamily,
                    color = MotoTracker.colors.accent,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // App name — 34 sp, uppercase per spec
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = MotoTracker.typography.screenTitle.copy(fontSize = 34.sp),
            color = MotoTracker.colors.text,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.tagline),
            style = MotoTracker.typography.bodySmall,
            color = MotoTracker.colors.dim,
        )

        // 🔬 Session-expired notice (B22) — shown when AUTHED was persisted but cookie is gone.
        if (sessionExpired) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.login_session_expired),
                style = MotoTracker.typography.bodySmall,
                color = MotoTracker.colors.accent2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(32.dp))

        // Server address — monospace font (URLs are data values)
        LoginFieldLabel(stringResource(R.string.label_server_address))
        OutlinedTextField(
            value = uiState.serverAddress,
            onValueChange = onServerAddress,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MotoTracker.typography.body.copy(fontFamily = JetBrainsMonoFamily),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = loginFieldColors(),
        )

        Spacer(Modifier.height(12.dp))

        // E-mail
        LoginFieldLabel(stringResource(R.string.label_email))
        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmail,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MotoTracker.typography.body,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = loginFieldColors(),
        )

        Spacer(Modifier.height(12.dp))

        // Password
        LoginFieldLabel(stringResource(R.string.label_password))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPassword,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MotoTracker.typography.body,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = loginFieldColors(),
        )

        Spacer(Modifier.height(24.dp))

        // Primary: sign in & sync
        Button(
            onClick = onSignIn,
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
                    text = stringResource(R.string.btn_sign_in_sync).uppercase(),
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

        Spacer(Modifier.height(8.dp))

        // Secondary: continue without account
        OutlinedButton(
            onClick = onGuest,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MotoTracker.colors.line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MotoTracker.colors.text),
        ) {
            Text(
                text = stringResource(R.string.btn_guest_continue).uppercase(),
                style = MotoTracker.typography.label,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Dim caption explaining guest mode
        Text(
            text = stringResource(R.string.note_guest_mode),
            style = MotoTracker.typography.bodySmall,
            color = MotoTracker.colors.dim,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        // Link to registration screen
        androidx.compose.material3.TextButton(onClick = onCreateAccount) {
            Text(
                text = stringResource(R.string.login_create_account),
                style = MotoTracker.typography.bodySmall,
                color = MotoTracker.colors.dim,
            )
        }
    }
}

@Composable
private fun LoginFieldLabel(text: String) {
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
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MotoTracker.colors.text,
    unfocusedTextColor = MotoTracker.colors.text,
    focusedBorderColor = MotoTracker.colors.accent,
    unfocusedBorderColor = MotoTracker.colors.line,
    cursorColor = MotoTracker.colors.accent,
)
