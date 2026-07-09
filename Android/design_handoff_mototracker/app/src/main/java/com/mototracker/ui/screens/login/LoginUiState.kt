package com.mototracker.ui.screens.login

/**
 * Immutable snapshot of the Login screen UI state.
 *
 * @param serverAddress Base URL of the GPStrack server; pre-filled from persisted settings.
 * @param email         E-mail address field content; empty until the user types.
 * @param password      Password field content; empty until the user types.
 * @param canSubmit     `true` when [serverAddress] is non-blank after trimming.
 */
data class LoginUiState(
    val serverAddress: String = "",
    val email: String = "",
    val password: String = "",
    val canSubmit: Boolean = false,
)
