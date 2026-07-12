package com.mototracker.data.auth

/**
 * Persisted onboarding/authentication choice for a MotoTracker user.
 *
 * [NONE] is the initial state on a fresh install, or after an explicit sign-out.
 *        The Login screen is shown on the next launch.
 * [GUEST] means the user chose "Continue as guest". The main app shell is shown
 *         on the next launch; no server sync is attempted.
 * [AUTHED] means the user authenticated successfully. The main app shell is shown
 *          on the next launch if a valid session cookie is also present; otherwise
 *          the Login screen is shown with a session-expired notice.
 */
enum class AuthState { NONE, GUEST, AUTHED }
