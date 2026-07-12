package com.mototracker.ui.state

import com.mototracker.data.auth.AuthState

/**
 * One-time startup navigation decision derived from the persisted [AuthState] and live session.
 *
 * [Loading] is the initial sentinel emitted before both DataStore sources have produced
 * their first values. [Ready] carries the fully-resolved navigation target.
 */
sealed interface StartupDecision {

    /** Emitted while the DataStore and session sources are still initializing. */
    data object Loading : StartupDecision

    /**
     * The decision is fully resolved; the UI may render.
     *
     * @param startScreen     The [AppScreen] to use as the navigation start destination.
     * @param authed          Whether the user is currently authenticated with a valid session.
     * @param sessionExpired  True when [AuthState.AUTHED] is persisted but no session cookie is
     *                        present — the Login screen shows a re-login notice.
     */
    data class Ready(
        val startScreen: AppScreen,
        val authed: Boolean,
        val sessionExpired: Boolean = false,
    ) : StartupDecision
}

/**
 * Pure, deterministic mapping from persisted auth state and live session to a start screen.
 *
 * No side-effects; safe to call from unit tests without an Android runtime.
 *
 * @param authState            The persisted [AuthState] from DataStore.
 * @param sessionAuthenticated Whether a valid session cookie is currently present.
 * @return                     The [AppScreen] to navigate to on launch.
 */
fun startScreenFor(authState: AuthState, sessionAuthenticated: Boolean): AppScreen = when (authState) {
    AuthState.AUTHED -> if (sessionAuthenticated) AppScreen.MAIN else AppScreen.LOGIN
    AuthState.GUEST  -> AppScreen.MAIN
    AuthState.NONE   -> AppScreen.LOGIN
}
