package com.mototracker.ui.screens.login

/** One-shot navigation event emitted by [LoginViewModel] via its [LoginViewModel.events] flow. */
sealed class LoginEvent {

    /**
     * Instructs the UI to navigate away from the login screen to the main app shell.
     *
     * @param authed `true` if the user authenticated (sign-in path), `false` for guest mode.
     */
    data class NavigateToMain(val authed: Boolean) : LoginEvent()
}
