package com.mototracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.ui.navigation.MotoApp
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
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Resolve AppStateViewModel against the Activity's ViewModelStoreOwner so the
            // instance is shared with SettingsScreen (which also resolves against the Activity).
            val appViewModel: com.mototracker.ui.state.AppStateViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(this@MainActivity)
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
