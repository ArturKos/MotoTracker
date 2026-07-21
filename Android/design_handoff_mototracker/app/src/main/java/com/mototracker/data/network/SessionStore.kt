package com.mototracker.data.network

import kotlinx.coroutines.flow.Flow

/**
 * Snapshot of the current GPStrack authentication state.
 *
 * @param cookie      Raw `name=value` pair from the server's `Set-Cookie` header
 *                    (e.g. `PHPSESSID=abc`), or null when unauthenticated.
 * @param email       E-mail address of the authenticated user, or null when unauthenticated.
 * @param writeApiKey Per-user write API key returned by login/register, or null when absent.
 *                    Used as a Bearer token for route uploads to avoid session-expiry re-logins.
 */
data class SessionState(
    val cookie: String?,
    val email: String?,
    val writeApiKey: String? = null,
) {
    /** True when a session cookie or a write API key is present. */
    val isAuthenticated: Boolean get() = cookie != null || writeApiKey != null

    companion object {
        /** Sentinel for the unauthenticated state. */
        val UNAUTHENTICATED = SessionState(cookie = null, email = null, writeApiKey = null)
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
     * Persists session credentials atomically.
     *
     * A null [cookie] or null [writeApiKey] removes the corresponding stored value.
     * At least one of the two should be non-null for [SessionState.isAuthenticated] to hold.
     *
     * @param cookie      Raw `name=value` pair from the server's `Set-Cookie` header, or null.
     * @param email       E-mail address used for authentication.
     * @param writeApiKey Per-user write API key returned by login/register, or null.
     */
    suspend fun save(cookie: String?, email: String, writeApiKey: String?)

    /** Removes the persisted session; [session] will subsequently emit [SessionState.UNAUTHENTICATED]. */
    suspend fun clear()
}
