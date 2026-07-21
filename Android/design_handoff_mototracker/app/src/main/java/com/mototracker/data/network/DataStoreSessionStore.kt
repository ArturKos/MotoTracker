package com.mototracker.data.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SessionStore] backed by the app's singleton [DataStore]<[Preferences]>.
 *
 * Reuses the same DataStore provided by [com.mototracker.di.SettingsModule] under
 * dedicated keys (`session_cookie`, `session_email`, `session_write_api_key`) that do not
 * collide with [com.mototracker.data.settings.SettingsDataStore]'s keys.
 *
 * @param dataStore Injected singleton [DataStore]<[Preferences]> instance.
 */
@Singleton
class DataStoreSessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SessionStore {

    private object Keys {
        val SESSION_COOKIE = stringPreferencesKey("session_cookie")
        val SESSION_EMAIL = stringPreferencesKey("session_email")
        val SESSION_WRITE_API_KEY = stringPreferencesKey("session_write_api_key")
    }

    /** Live stream of the current [SessionState], backed by the Preferences DataStore. */
    override val session: Flow<SessionState> = dataStore.data.map { prefs ->
        SessionState(
            cookie = prefs[Keys.SESSION_COOKIE],
            email = prefs[Keys.SESSION_EMAIL],
            writeApiKey = prefs[Keys.SESSION_WRITE_API_KEY],
        )
    }

    /**
     * Atomically writes session credentials to the DataStore.
     *
     * A null [cookie] or null [writeApiKey] removes the corresponding key rather than
     * storing an empty string, so [SessionState.cookie] and [SessionState.writeApiKey]
     * are always either a non-blank value or null.
     *
     * @param cookie      Raw `name=value` pair from the server's `Set-Cookie` header, or null.
     * @param email       E-mail address used for authentication.
     * @param writeApiKey Per-user write API key, or null.
     */
    override suspend fun save(cookie: String?, email: String, writeApiKey: String?) {
        dataStore.edit { prefs ->
            if (cookie != null) prefs[Keys.SESSION_COOKIE] = cookie
            else prefs.remove(Keys.SESSION_COOKIE)
            prefs[Keys.SESSION_EMAIL] = email
            if (writeApiKey != null) prefs[Keys.SESSION_WRITE_API_KEY] = writeApiKey
            else prefs.remove(Keys.SESSION_WRITE_API_KEY)
        }
    }

    /** Removes all three session keys from the DataStore atomically. */
    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SESSION_COOKIE)
            prefs.remove(Keys.SESSION_EMAIL)
            prefs.remove(Keys.SESSION_WRITE_API_KEY)
        }
    }
}
