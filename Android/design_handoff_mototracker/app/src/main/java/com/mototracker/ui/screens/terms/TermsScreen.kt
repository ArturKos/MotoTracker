package com.mototracker.ui.screens.terms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker

/**
 * First-launch terms/disclaimer gate screen (J2).
 *
 * Displayed before the main app shell on a user's very first launch (or whenever the persisted
 * acceptance flag is absent/false). The user must either Accept to continue or Decline to exit.
 *
 * The screen is full-screen and vertically scrollable so the full disclaimer copy is readable
 * on small displays. It sits inside [MotoTrackerTheme] so it honours the active theme.
 *
 * @param onAccept  Called when the user taps the Accept button. The caller persists the flag
 *                  and updates the startup gate.
 * @param onDecline Called when the user taps the Decline button. The caller should call
 *                  [android.app.Activity.finishAndRemoveTask] to exit the app.
 * @param modifier  Standard Compose modifier.
 */
@Composable
fun TermsScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MotoTracker.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.terms_title),
            color = MotoTracker.colors.text,
            style = MotoTracker.typography.body.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = stringResource(R.string.terms_lead),
            color = MotoTracker.colors.dim,
            style = MotoTracker.typography.label,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        HorizontalDivider(
            color = MotoTracker.colors.line,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        Text(
            text = stringResource(R.string.terms_no_liability),
            color = MotoTracker.colors.text,
            style = MotoTracker.typography.body,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = stringResource(R.string.terms_no_riding),
            color = MotoTracker.colors.text,
            style = MotoTracker.typography.body,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MotoTracker.colors.accent,
                contentColor = MotoTracker.colors.bg,
            ),
        ) {
            Text(
                text = stringResource(R.string.terms_accept),
                style = MotoTracker.typography.body.copy(fontWeight = FontWeight.SemiBold),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MotoTracker.colors.dim,
            ),
        ) {
            Text(
                text = stringResource(R.string.terms_decline),
                style = MotoTracker.typography.body,
            )
        }
    }
}
