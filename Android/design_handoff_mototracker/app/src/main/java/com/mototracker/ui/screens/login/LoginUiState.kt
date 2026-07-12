package com.mototracker.ui.screens.login

import androidx.annotation.StringRes

/**
 * Immutable snapshot of the Login screen UI state.
 *
 * @param serverAddress  Base URL of the GPStrack server; pre-filled from persisted settings.
 * @param email          E-mail address field content; empty until the user types.
 * @param password       Password field content; empty until the user types.
 * @param canSubmit      `true` when [serverAddress] is non-blank after trimming and [loading] is `false`.
 * @param loading        `true` while an authentication request is in flight.
 * @param errorMessage   String resource id of the inline error to display, or `null` when there is none.
 */
data class LoginUiState(
    val serverAddress: String = "",
    val email: String = "",
    val password: String = "",
    val canSubmit: Boolean = false,
    val loading: Boolean = false,
    @StringRes val errorMessage: Int? = null,
)
