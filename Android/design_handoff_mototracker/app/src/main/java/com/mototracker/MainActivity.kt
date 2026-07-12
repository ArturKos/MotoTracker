package com.mototracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.ui.navigation.MotoApp
import com.mototracker.ui.state.AppScreen
import com.mototracker.ui.state.StartupDecision
import com.mototracker.ui.theme.MotoTracker
import com.mototracker.ui.theme.MotoTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point activity for the MotoTracker app.
 *
 * Extends [AppCompatActivity] (instead of [androidx.activity.ComponentActivity]) so that
 * [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales] is honoured for per-app
 * locale switching; Compose [setContent] and [enableEdgeToEdge] remain fully supported because
 * [AppCompatActivity] is a subclass of [androidx.activity.ComponentActivity].
 *
 * Annotated with [AndroidEntryPoint] to enable Hilt injection. Sets up the Compose
 * content root: edge-to-edge rendering, [MotoTrackerTheme] driven by the activity-scoped
 * [com.mototracker.ui.state.AppStateViewModel], and the navigation shell [MotoApp].
 *
 * While [StartupDecision.Loading] is pending (DataStore sources not yet emitted), only the
 * themed background is rendered — preventing a Login flash for users who previously signed in
 * or chose guest mode (B22).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: com.mototracker.ui.state.AppStateViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(this@MainActivity)
            val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
            val recordingActive by appViewModel.recordingActive.collectAsStateWithLifecycle()
            val startupDecision by appViewModel.startupDecision.collectAsStateWithLifecycle()
            val syncState by appViewModel.syncState.collectAsStateWithLifecycle()

            MotoTrackerTheme(
                theme = uiState.theme,
                accent = uiState.accent,
            ) {
                when (val decision = startupDecision) {
                    is StartupDecision.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MotoTracker.colors.bg),
                        )
                    }
                    is StartupDecision.Ready -> {
                        MotoApp(
                            startAtMain = decision.startScreen == AppScreen.MAIN,
                            sessionExpired = decision.sessionExpired,
                            onSignIn = appViewModel::signIn,
                            onContinueAsGuest = appViewModel::continueAsGuest,
                            recordingActive = recordingActive,
                            syncState = syncState,
                        )
                    }
                }
            }
        }
    }
}
