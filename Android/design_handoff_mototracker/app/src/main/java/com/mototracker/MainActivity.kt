package com.mototracker

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.ui.navigation.MotoApp
import com.mototracker.ui.screens.splash.SplashGate
import com.mototracker.ui.screens.splash.SplashPhase
import com.mototracker.ui.screens.splash.SplashScreen
import com.mototracker.ui.screens.terms.TermsScreen
import com.mototracker.ui.state.AppScreen
import com.mototracker.ui.state.StartupDecision
import com.mototracker.ui.theme.MotoTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

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
 * While [StartupDecision.Loading] is pending (DataStore sources not yet emitted), a branded
 * [SplashScreen] is shown. The splash respects a minimum visible duration ([SplashGate.DEFAULT_MIN_MS])
 * so it is always seen on fast devices, and a hard cap ([SplashGate.DEFAULT_MAX_MS]) to prevent
 * an infinite hang — preventing a Login flash for users who previously signed in or chose guest
 * mode (B22).
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

            // Track splash start time and elapsed for SplashGate.
            val splashStartMs = remember { SystemClock.elapsedRealtime() }
            var splashElapsedMs by remember { mutableLongStateOf(0L) }
            var splashPhase by remember { mutableStateOf(SplashPhase.INITIALIZING) }
            val isReady = startupDecision is StartupDecision.Ready

            // Tick elapsed time every 50 ms while the splash might still be visible.
            LaunchedEffect(isReady) {
                while (!SplashGate.shouldDismiss(isReady, splashElapsedMs)) {
                    delay(50L)
                    splashElapsedMs = SystemClock.elapsedRealtime() - splashStartMs
                }
            }

            // Advance phase: MIGRATING_DB after 400 ms if still loading, then READY.
            LaunchedEffect(isReady) {
                if (!isReady) {
                    delay(400L)
                    if (!isReady) splashPhase = SplashPhase.MIGRATING_DB
                } else {
                    splashPhase = SplashPhase.READY
                }
            }

            val showSplash = !SplashGate.shouldDismiss(isReady, splashElapsedMs)

            MotoTrackerTheme(
                theme = uiState.theme,
                accent = uiState.accent,
            ) {
                if (showSplash) {
                    SplashScreen(phase = splashPhase)
                } else {
                    when (val decision = startupDecision) {
                        is StartupDecision.Loading -> {
                            // Still loading but past the max timeout — show splash until
                            // the gate finally allows dismissal; this branch is unreachable
                            // in practice because the gate force-dismisses at maxDurationMs.
                            SplashScreen(phase = splashPhase)
                        }
                        is StartupDecision.Ready -> {
                            if (!decision.termsAccepted) {
                                TermsScreen(
                                    onAccept = appViewModel::acceptTerms,
                                    onDecline = { finishAndRemoveTask() },
                                )
                            } else {
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
    }
}
