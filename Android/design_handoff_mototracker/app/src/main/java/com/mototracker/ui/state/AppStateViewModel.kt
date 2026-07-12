package com.mototracker.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.i18n.LocaleController
import com.mototracker.data.auth.AuthState
import com.mototracker.data.auth.AuthStateStore
import com.mototracker.data.network.SessionStore
import com.mototracker.ui.navigation.isRecordingLocked
import com.mototracker.ui.theme.AccentColor
import com.mototracker.ui.theme.MotoTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-level ViewModel holding the single source of truth for global UI state.
 *
 * Exposed as a unidirectional [StateFlow]: consumers observe [uiState] and trigger updates only
 * via the named intent methods below. Internal state is never mutated directly from outside.
 *
 * [localeController] is injected to keep this ViewModel testable without an Android runtime;
 * fakes replace it in unit tests.
 *
 * [recordingBridge] derives [recordingActive] without coupling to the nav-scoped
 * [com.mototracker.ui.screens.record.RecordingViewModel].
 *
 * [authStateStore] and [sessionStore] persist the onboarding/auth choice and the server
 * session cookie so the Login screen is skipped on subsequent launches (B22).
 *
 * @param localeController  Applies per-app locale changes.
 * @param recordingBridge   Source of the current recording phase for [recordingActive].
 * @param authStateStore    Persists and reads the onboarding/auth choice.
 * @param sessionStore      Persists and reads the server session cookie.
 */
@HiltViewModel
class AppStateViewModel @Inject constructor(
    private val localeController: LocaleController,
    private val recordingBridge: CarRecordingBridge,
    private val authStateStore: AuthStateStore,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())

    /** Read-only view of the global UI state; never null, never throws. */
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    /**
     * Emits `true` whenever a recording session is active (phase ≠ Idle).
     *
     * Derived from [CarRecordingBridge.phase] via [isRecordingLocked]. Consumers
     * (e.g. [com.mototracker.MainActivity]) use this to lock bottom-nav tab switching and block
     * the back gesture while a ride is in progress.
     */
    val recordingActive: StateFlow<Boolean> = recordingBridge.phase
        .map { isRecordingLocked(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * One-time startup navigation decision, updated whenever the persisted auth state or session
     * cookie changes.
     *
     * Emits [StartupDecision.Loading] until both sources produce their first value, then emits
     * [StartupDecision.Ready] with the resolved [AppScreen] start destination, current auth flag,
     * and optional session-expired flag.
     */
    val startupDecision: StateFlow<StartupDecision> = combine(
        authStateStore.authState,
        sessionStore.session,
    ) { authState, session ->
        StartupDecision.Ready(
            startScreen = startScreenFor(authState, session.isAuthenticated),
            authed = authState == AuthState.AUTHED && session.isAuthenticated,
            sessionExpired = authState == AuthState.AUTHED && !session.isAuthenticated,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StartupDecision.Loading)

    /**
     * Marks the user as authenticated and navigates to the main app shell.
     *
     * Persists [AuthState.AUTHED] and updates [uiState] in-memory so the theme/nav shell
     * reflects the change immediately.
     */
    fun signIn() {
        _uiState.value = _uiState.value.copy(authed = true, screen = AppScreen.MAIN)
        viewModelScope.launch { authStateStore.set(AuthState.AUTHED) }
    }

    /**
     * Enters the app without authentication (guest / local-only mode).
     *
     * Persists [AuthState.GUEST]. No server sync is attempted for guest sessions.
     */
    fun continueAsGuest() {
        _uiState.value = _uiState.value.copy(authed = false, screen = AppScreen.MAIN)
        viewModelScope.launch { authStateStore.set(AuthState.GUEST) }
    }

    /**
     * Signs out and resets all state to defaults, returning to the login screen.
     *
     * Persists [AuthState.NONE], clears the session cookie, and reverts [uiState] to its
     * defaults (login screen, unauthenticated).
     */
    fun signOut() {
        _uiState.value = AppUiState()
        viewModelScope.launch {
            authStateStore.set(AuthState.NONE)
            sessionStore.clear()
        }
    }

    /**
     * Changes the active UI language and applies it via [localeController].
     *
     * @param language The [Language] to activate.
     */
    fun setLanguage(language: Language) {
        _uiState.value = _uiState.value.copy(language = language)
        localeController.applyLanguage(language.tag)
    }

    /**
     * Changes the active visual theme (cockpit / grid / light).
     *
     * @param theme The [MotoTheme] to activate.
     */
    fun setTheme(theme: MotoTheme) {
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    /**
     * Overrides the accent colour for the current theme.
     *
     * @param accent The [AccentColor] to activate.
     */
    fun setAccent(accent: AccentColor) {
        _uiState.value = _uiState.value.copy(accent = accent)
    }

    /**
     * Changes the measurement system preference (metric / imperial).
     *
     * @param units The [Units] to activate.
     */
    fun setUnits(units: Units) {
        _uiState.value = _uiState.value.copy(units = units)
    }
}
