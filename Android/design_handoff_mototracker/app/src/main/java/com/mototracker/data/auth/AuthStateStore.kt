package com.mototracker.data.auth

import kotlinx.coroutines.flow.Flow

/**
 * Persists and exposes the user's onboarding/authentication choice.
 *
 * Mirrors [com.mototracker.data.network.SessionStore] in structure.
 * All implementations must be main-safe.
 */
interface AuthStateStore {

    /**
     * Live stream of the current [AuthState].
     *
     * Emits immediately with the persisted value on first collection, then on every subsequent
     * change. Absent or unrecognized stored values yield [AuthState.NONE].
     */
    val authState: Flow<AuthState>

    /**
     * Persists [state] as the current auth state.
     *
     * @param state The [AuthState] to write to storage.
     */
    suspend fun set(state: AuthState)
}
