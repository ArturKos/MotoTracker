package com.mototracker.ui.screens.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.R
import com.mototracker.data.network.EmailTakenException
import com.mototracker.data.network.GpStrackClient
import com.mototracker.data.network.InvalidRegistrationException
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

/** Simple RFC-5322-compatible e-mail regex usable in plain JVM (no Android dependency). */
private val EMAIL_REGEX = Regex(
    "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$"
)

private const val MIN_PASSWORD_LENGTH = 8

/**
 * ViewModel for the Register screen.
 *
 * Initialises [uiState] with the persisted server address from [settingsSource] on creation.
 * Field-update intents recompute [RegisterUiState.canSubmit] and clear any inline error on every
 * change. [register] performs registration via [client]: on success it drains the offline sync
 * queue via [syncRepository] (best-effort) then emits [RegisterEvent.NavigateToMain]; on failure
 * it surfaces a localized string resource id in [RegisterUiState.errorMessage] and leaves the
 * form editable — [RegisterEvent.NavigateToMain] is NOT emitted.
 *
 * @param settingsSource  Provides the live settings flow and persistence for the server address.
 * @param client          GPStrack HTTP client; used to call the `/register.php` endpoint.
 * @param syncRepository  Manages the outbound sync queue; drained best-effort after registration.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val settingsSource: WritableSettingsSource,
    private val client: GpStrackClient,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())

    /** Current state of the registration form; never null. */
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RegisterEvent>()

    /** One-shot navigation events; collect with [androidx.compose.runtime.LaunchedEffect]. */
    val events: SharedFlow<RegisterEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val initial = settingsSource.settings.first()
            val addr = initial.serverAddress
            _uiState.update { it.copy(serverAddress = addr) }
        }
    }

    /**
     * Updates the server address field and recomputes [RegisterUiState.canSubmit].
     *
     * @param address Raw string from the text field.
     */
    fun updateServerAddress(address: String) {
        _uiState.update { state ->
            state.copy(
                serverAddress = address,
                canSubmit = computeCanSubmit(address, state.email, state.password, state.confirm, false),
                errorMessage = null,
            )
        }
    }

    /**
     * Updates the e-mail field, recomputes [RegisterUiState.canSubmit], and clears any inline error.
     *
     * @param email Raw string from the text field.
     */
    fun updateEmail(email: String) {
        _uiState.update { state ->
            state.copy(
                email = email,
                canSubmit = computeCanSubmit(state.serverAddress, email, state.password, state.confirm, false),
                errorMessage = null,
            )
        }
    }

    /**
     * Updates the password field, recomputes [RegisterUiState.canSubmit], and clears any inline error.
     *
     * @param password Raw string from the text field.
     */
    fun updatePassword(password: String) {
        _uiState.update { state ->
            state.copy(
                password = password,
                canSubmit = computeCanSubmit(state.serverAddress, state.email, password, state.confirm, false),
                errorMessage = null,
            )
        }
    }

    /**
     * Updates the confirm-password field, recomputes [RegisterUiState.canSubmit], and clears any inline error.
     *
     * @param confirm Raw string from the confirm-password text field.
     */
    fun updateConfirm(confirm: String) {
        _uiState.update { state ->
            state.copy(
                confirm = confirm,
                canSubmit = computeCanSubmit(state.serverAddress, state.email, state.password, confirm, false),
                errorMessage = null,
            )
        }
    }

    /**
     * Registers with the GPStrack server using the current form values.
     *
     * Persists the trimmed [RegisterUiState.serverAddress], sets [RegisterUiState.loading] to `true`,
     * then calls [GpStrackClient.register]. On success the offline sync queue is drained
     * (best-effort; a drain failure does not block navigation) and
     * [RegisterEvent.NavigateToMain] with `authed = true` is emitted. On failure
     * [RegisterUiState.errorMessage] is set to a localized string resource id and the form is
     * left editable — [RegisterEvent.NavigateToMain] is NOT emitted.
     * [EmailTakenException] maps to [R.string.register_error_email_taken];
     * [InvalidRegistrationException] maps to [R.string.register_error_invalid_input];
     * any other error maps to [R.string.register_error_network].
     */
    fun register() {
        viewModelScope.launch {
            val snapshot = _uiState.value
            val trimmed = snapshot.serverAddress.trim()
            settingsSource.setServerAddress(trimmed)
            _uiState.update { it.copy(loading = true, errorMessage = null, canSubmit = false) }

            val result = client.register(trimmed, snapshot.email.trim(), snapshot.password)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(loading = false) }
                    runCatching { syncRepository.syncNow() }
                    _events.emit(RegisterEvent.NavigateToMain(authed = true))
                },
                onFailure = { error ->
                    val errorRes = when (error) {
                        is EmailTakenException -> R.string.register_error_email_taken
                        is InvalidRegistrationException -> R.string.register_error_invalid_input
                        else -> R.string.register_error_network
                    }
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            errorMessage = errorRes,
                            canSubmit = computeCanSubmit(
                                state.serverAddress, state.email, state.password, state.confirm, false
                            ),
                        )
                    }
                },
            )
        }
    }

    /** Emits [RegisterEvent.NavigateBackToLogin] so the screen navigates back. */
    fun navigateBackToLogin() {
        viewModelScope.launch {
            _events.emit(RegisterEvent.NavigateBackToLogin)
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun computeCanSubmit(
        serverAddress: String,
        email: String,
        password: String,
        confirm: String,
        loading: Boolean,
    ): Boolean {
        if (loading) return false
        if (serverAddress.trim().isBlank()) return false
        if (!EMAIL_REGEX.matches(email.trim())) return false
        if (password.length < MIN_PASSWORD_LENGTH) return false
        if (password != confirm) return false
        return true
    }
}
