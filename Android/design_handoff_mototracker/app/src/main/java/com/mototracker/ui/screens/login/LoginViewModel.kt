package com.mototracker.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.data.settings.WritableSettingsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Login screen.
 *
 * Initialises [uiState] with the persisted server address from [settingsSource] on creation.
 * Field-update intents recompute [LoginUiState.canSubmit] on every change.
 * [signIn] and [continueAsGuest] persist the trimmed address then emit a one-shot [LoginEvent]
 * via [events]; the composable collects these to drive navigation.
 *
 * No real authentication backend — sign-in simply saves the address and marks the session authed.
 *
 * @param settingsSource Provides the live settings flow and the ability to persist the server address.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val settingsSource: WritableSettingsSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())

    /** Current state of the login form; never null. */
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()

    /** One-shot navigation events; collect with [androidx.compose.runtime.LaunchedEffect]. */
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val initial = settingsSource.settings.first()
            val addr = initial.serverAddress
            _uiState.update { it.copy(serverAddress = addr, canSubmit = addr.trim().isNotBlank()) }
        }
    }

    /**
     * Updates the server address field and recomputes [LoginUiState.canSubmit].
     *
     * @param address Raw string from the text field.
     */
    fun updateServerAddress(address: String) {
        _uiState.update { it.copy(serverAddress = address, canSubmit = address.trim().isNotBlank()) }
    }

    /**
     * Updates the e-mail field.
     *
     * @param email Raw string from the text field.
     */
    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    /**
     * Updates the password field.
     *
     * @param password Raw string from the text field.
     */
    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    /**
     * Persists the trimmed [LoginUiState.serverAddress] and emits
     * [LoginEvent.NavigateToMain] with `authed = true`.
     *
     * No real credential verification is performed; the server address is simply saved
     * and the user is considered authenticated for the current session.
     */
    fun signIn() {
        viewModelScope.launch {
            val trimmed = _uiState.value.serverAddress.trim()
            settingsSource.setServerAddress(trimmed)
            _events.emit(LoginEvent.NavigateToMain(authed = true))
        }
    }

    /**
     * Persists the trimmed [LoginUiState.serverAddress] and emits
     * [LoginEvent.NavigateToMain] with `authed = false`.
     *
     * The user enters guest mode: data is saved locally only and no server sync is attempted.
     */
    fun continueAsGuest() {
        viewModelScope.launch {
            val trimmed = _uiState.value.serverAddress.trim()
            settingsSource.setServerAddress(trimmed)
            _events.emit(LoginEvent.NavigateToMain(authed = false))
        }
    }
}
