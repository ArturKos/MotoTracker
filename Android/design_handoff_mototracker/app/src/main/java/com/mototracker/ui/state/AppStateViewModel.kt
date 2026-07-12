package com.mototracker.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.i18n.LocaleController
import com.mototracker.ui.navigation.isRecordingLocked
import com.mototracker.ui.theme.AccentColor
import com.mototracker.ui.theme.MotoTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * App-level ViewModel holding the single source of truth for global UI state.
 *
 * Exposed as a unidirectional [StateFlow]: consumers observe [uiState] and
 * trigger updates only via the named intent methods below. Internal state is
 * never mutated directly from outside this class.
 *
 * [localeController] is injected to keep this ViewModel testable without an
 * Android runtime; fakes replace it in unit tests.
 *
 * [recordingBridge] is injected to observe the current recording phase and derive
 * [recordingActive] without coupling this ViewModel to the nav-scoped
 * [com.mototracker.ui.screens.record.RecordingViewModel].
 *
 * NOTE (A6): Persistence is intentionally absent here. Task A6 will replace the
 * in-memory [MutableStateFlow] initialiser with a DataStore-backed source and
 * make intent methods suspend functions that also persist to disk.
 */
@HiltViewModel
class AppStateViewModel @Inject constructor(
    private val localeController: LocaleController,
    private val recordingBridge: CarRecordingBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())

    /** Read-only view of the global UI state; never null, never throws. */
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    /**
     * Emits `true` whenever a recording session is active (phase ≠ Idle).
     *
     * Derived from [CarRecordingBridge.phase] via [isRecordingLocked]. Consumers
     * (e.g. [MainActivity]) use this to lock bottom-nav tab switching and block
     * the back gesture while a ride is in progress.
     */
    val recordingActive: StateFlow<Boolean> = recordingBridge.phase
        .map { isRecordingLocked(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Marks the user as authenticated and navigates to the main app shell.
     *
     * Sets [AppUiState.authed] to `true` and [AppUiState.screen] to [AppScreen.MAIN].
     */
    fun signIn() {
        _uiState.value = _uiState.value.copy(authed = true, screen = AppScreen.MAIN)
    }

    /**
     * Enters the app without authentication (guest / local-only mode).
     *
     * Sets [AppUiState.screen] to [AppScreen.MAIN] while keeping [AppUiState.authed]
     * as `false`. The app works fully offline; no server sync is attempted for guests.
     */
    fun continueAsGuest() {
        _uiState.value = _uiState.value.copy(authed = false, screen = AppScreen.MAIN)
    }

    /**
     * Signs out and resets all state to defaults, returning to the login screen.
     *
     * Equivalent to constructing a fresh [AppUiState] — all fields revert to defaults,
     * including [AppUiState.screen] = [AppScreen.LOGIN] and [AppUiState.authed] = `false`.
     */
    fun signOut() {
        _uiState.value = AppUiState()
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
