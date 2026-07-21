package com.mototracker.ui.screens.register

/** One-shot navigation event emitted by [RegisterViewModel] via its [RegisterViewModel.events] flow. */
sealed class RegisterEvent {

    /**
     * Instructs the UI to navigate away from the registration screen to the main app shell.
     *
     * @param authed `true` when the server returned a session cookie and the user is
     *               auto-logged-in; `false` when registration succeeded without a cookie.
     */
    data class NavigateToMain(val authed: Boolean) : RegisterEvent()

    /** Instructs the UI to navigate back to the Login screen. */
    object NavigateBackToLogin : RegisterEvent()
}
