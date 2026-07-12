package com.mototracker.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.R
import com.mototracker.data.network.GpStrackClient
import com.mototracker.data.network.UnauthorizedException
import com.mototracker.data.repository.SyncRepository
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
 * Field-update intents recompute [LoginUiState.canSubmit] and clear any inline error on every
 * change. [signIn] performs real authentication via [client]: on success it drains the offline
 * sync queue via [syncRepository] (best-effort; a drain failure does not block navigation) then
 * emits [LoginEvent.NavigateToMain] with `authed = true`; on failure it surfaces a localized
 * error string resource id in [LoginUiState.errorMessage] and leaves the form editable —
 * [LoginEvent.NavigateToMain] is NOT emitted. [continueAsGuest] persists the address and
 * navigates without performing any network call.
 *
 * @param settingsSource  Provides the live settings flow and the ability to persist the server address.
 * @param client          GPStrack HTTP client; used to call the `/login` endpoint.
 * @param syncRepository  Manages the outbound sync queue; [SyncRepository.syncNow] is drained
 *                        after a successful sign-in.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val settingsSource: WritableSettingsSource,
    private val client: GpStrackClient,
    private val syncRepository: SyncRepository,
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
     * Updates the server address field, recomputes [LoginUiState.canSubmit], and clears any
     * inline [LoginUiState.errorMessage].
     *
     * @param address Raw string from the text field.
     */
    fun updateServerAddress(address: String) {
        _uiState.update {
            it.copy(
                serverAddress = address,
                canSubmit = address.trim().isNotBlank() && !it.loading,
                errorMessage = null,
            )
        }
    }

    /**
     * Updates the e-mail field and clears any inline [LoginUiState.errorMessage].
     *
     * @param email Raw string from the text field.
     */
    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    /**
     * Updates the password field and clears any inline [LoginUiState.errorMessage].
     *
     * @param password Raw string from the text field.
     */
    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    /**
     * Authenticates with the GPStrack server using the current form values.
     *
     * Persists the trimmed [LoginUiState.serverAddress], sets [LoginUiState.loading] to `true`,
     * then calls [GpStrackClient.login]. On success the offline sync queue is drained
     * (best-effort; a drain failure does not block navigation) and
     * [LoginEvent.NavigateToMain] with `authed = true` is emitted. On failure
     * [LoginUiState.errorMessage] is set to a localized string resource id and the form is
     * left editable — [LoginEvent.NavigateToMain] is NOT emitted.
     * [UnauthorizedException] maps to [R.string.login_error_invalid_credentials]; any other
     * error maps to [R.string.login_error_network].
     */
    fun signIn() {
        viewModelScope.launch {
            val snapshot = _uiState.value
            val trimmed = snapshot.serverAddress.trim()
            settingsSource.setServerAddress(trimmed)
            _uiState.update { it.copy(loading = true, errorMessage = null, canSubmit = false) }

            val result = client.login(trimmed, snapshot.email, snapshot.password)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(loading = false) }
                    runCatching { syncRepository.syncNow() }
                    _events.emit(LoginEvent.NavigateToMain(authed = true))
                },
                onFailure = { error ->
                    val errorRes = if (error is UnauthorizedException) {
                        R.string.login_error_invalid_credentials
                    } else {
                        R.string.login_error_network
                    }
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            errorMessage = errorRes,
                            canSubmit = state.serverAddress.trim().isNotBlank(),
                        )
                    }
                },
            )
        }
    }

    /**
     * Persists the trimmed [LoginUiState.serverAddress] and emits
     * [LoginEvent.NavigateToMain] with `authed = false`.
     *
     * The user enters guest mode: data is saved locally only and no server sync is attempted.
     * No authentication call is made.
     */
    fun continueAsGuest() {
        viewModelScope.launch {
            val trimmed = _uiState.value.serverAddress.trim()
            settingsSource.setServerAddress(trimmed)
            _events.emit(LoginEvent.NavigateToMain(authed = false))
        }
    }
}
