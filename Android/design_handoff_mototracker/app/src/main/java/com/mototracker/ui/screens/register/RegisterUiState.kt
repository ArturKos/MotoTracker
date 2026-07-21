package com.mototracker.ui.screens.register

import androidx.annotation.StringRes

/**
 * Immutable snapshot of the Register screen UI state.
 *
 * @param serverAddress  Base URL of the GPStrack server; pre-filled from persisted settings.
 * @param email          E-mail address field content.
 * @param password       Password field content.
 * @param confirm        Password-confirmation field content.
 * @param canSubmit      `true` when all fields are valid, [serverAddress] is non-blank, and
 *                       [loading] is `false`.
 * @param loading        `true` while a registration request is in flight.
 * @param errorMessage   String resource id of the inline error to display, or `null` when
 *                       there is none.
 */
data class RegisterUiState(
    val serverAddress: String = "",
    val email: String = "",
    val password: String = "",
    val confirm: String = "",
    val canSubmit: Boolean = false,
    val loading: Boolean = false,
    @StringRes val errorMessage: Int? = null,
)
