package com.mototracker.data.network

import kotlinx.coroutines.flow.Flow

/**
 * Snapshot of the current GPStrack authentication state.
 *
 * @param cookie Raw `name=value` pair from the server's `Set-Cookie` header
 *               (e.g. `PHPSESSID=abc`), or null when unauthenticated.
 * @param email  E-mail address of the authenticated user, or null when unauthenticated.
 */
data class SessionState(
    val cookie: String?,
    val email: String?,
) {
    /** True when a session cookie is present. */
    val isAuthenticated: Boolean get() = cookie != null

    companion object {
        /** Sentinel for the unauthenticated state. */
        val UNAUTHENTICATED = SessionState(cookie = null, email = null)
    }
}

/**
 * Persists and exposes the current GPStrack session cookie.
 *
 * Implementations must be main-safe.
 */
interface SessionStore {

    /**
     * Live stream of the current [SessionState].
     *
     * Emits immediately with the persisted value on first collection, then on
     * every subsequent change.
     */
    val session: Flow<SessionState>

    /**
     * Persists [cookie] and [email] as the current authenticated session.
     *
     * @param cookie Raw `name=value` pair from the server's `Set-Cookie` header.
     * @param email  E-mail address used for authentication.
     */
    suspend fun save(cookie: String, email: String)

    /** Removes the persisted session; [session] will subsequently emit [SessionState.UNAUTHENTICATED]. */
    suspend fun clear()
}
