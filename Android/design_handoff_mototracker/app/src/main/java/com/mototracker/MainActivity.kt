package com.mototracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.ui.navigation.MotoApp
import com.mototracker.ui.theme.MotoTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point activity for the MotoTracker app.
 *
 * Annotated with [AndroidEntryPoint] to enable Hilt injection. Sets up the Compose
 * content root: edge-to-edge rendering, [MotoTrackerTheme] driven by the app-level
 * [com.mototracker.ui.state.AppStateViewModel], and the navigation shell [MotoApp].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: com.mototracker.ui.state.AppStateViewModel =
                androidx.hilt.navigation.compose.hiltViewModel()
            val uiState by appViewModel.uiState.collectAsStateWithLifecycle()

            MotoTrackerTheme(
                theme = uiState.theme,
                accent = uiState.accent,
            ) {
                MotoApp(
                    authed = uiState.authed,
                    onSignIn = appViewModel::signIn,
                    onContinueAsGuest = appViewModel::continueAsGuest,
                )
            }
        }
    }
}
