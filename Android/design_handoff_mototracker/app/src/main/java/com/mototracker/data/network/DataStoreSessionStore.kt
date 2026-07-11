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
 * dedicated keys (`session_cookie`, `session_email`) that do not collide with
 * [com.mototracker.data.settings.SettingsDataStore]'s keys.
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
    }

    /** Live stream of the current [SessionState], backed by the Preferences DataStore. */
    override val session: Flow<SessionState> = dataStore.data.map { prefs ->
        SessionState(
            cookie = prefs[Keys.SESSION_COOKIE],
            email = prefs[Keys.SESSION_EMAIL],
        )
    }

    /**
     * Atomically writes [cookie] and [email] to the DataStore.
     *
     * @param cookie Raw `name=value` pair from the server's `Set-Cookie` header.
     * @param email  E-mail address used for authentication.
     */
    override suspend fun save(cookie: String, email: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SESSION_COOKIE] = cookie
            prefs[Keys.SESSION_EMAIL] = email
        }
    }

    /** Removes both session keys from the DataStore atomically. */
    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SESSION_COOKIE)
            prefs.remove(Keys.SESSION_EMAIL)
        }
    }
}
