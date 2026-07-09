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
 * Login screen composable.
 *
 * Renders the server-address / e-mail / password form and two action buttons.
 * Navigation is driven by one-shot [LoginEvent]s collected from [viewModel.events]:
 * [LoginEvent.NavigateToMain] with `authed = true` triggers [onSignedIn], and
 * `authed = false` triggers [onGuest].
 *
 * All user-facing strings are loaded from string resources (pl/en/de/fr/cs/ru).
 *
 * @param onSignedIn Called after a successful sign-in flow; navigate to the main shell.
 * @param onGuest    Called when the user chooses to continue without an account.
 * @param modifier   Applied to the root container.
 * @param viewModel  Hilt-injected [LoginViewModel].
 */
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onGuest: () -> Unit,
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

        Spacer(Modifier.height(32.dp))

        // Server address — monospace font (URLs are data values)
        LoginFieldLabel(stringResource(R.string.label_server_address))
        OutlinedTextField(
            value = uiState.serverAddress,
            onValueChange = viewModel::updateServerAddress,
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
            onValueChange = viewModel::updateEmail,
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
            onValueChange = viewModel::updatePassword,
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
            onClick = viewModel::signIn,
            enabled = uiState.canSubmit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MotoTracker.colors.accent,
                contentColor = MotoTracker.colors.onAccent,
                disabledContainerColor = MotoTracker.colors.panel,
                disabledContentColor = MotoTracker.colors.dim,
            ),
        ) {
            Text(
                text = stringResource(R.string.btn_sign_in_sync).uppercase(),
                style = MotoTracker.typography.label,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Secondary: continue without account
        OutlinedButton(
            onClick = viewModel::continueAsGuest,
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
