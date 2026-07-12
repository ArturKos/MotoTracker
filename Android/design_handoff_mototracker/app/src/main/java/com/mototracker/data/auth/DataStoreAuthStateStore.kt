package com.mototracker.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AuthStateStore] backed by the app's singleton [DataStore]<[Preferences]>.
 *
 * Reuses the same DataStore provided by [com.mototracker.di.SettingsModule] under
 * a new, non-colliding key (`auth_state`). The enum is serialized by [AuthState.name];
 * absent or unrecognized stored values are mapped to [AuthState.NONE] without throwing.
 *
 * @param dataStore Injected singleton [DataStore]<[Preferences]> instance.
 */
@Singleton
class DataStoreAuthStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AuthStateStore {

    private object Keys {
        val AUTH_STATE = stringPreferencesKey("auth_state")
    }

    /** Live stream of the current [AuthState], backed by the Preferences DataStore. */
    override val authState: Flow<AuthState> = dataStore.data.map { prefs ->
        prefs[Keys.AUTH_STATE]?.let { name ->
            AuthState.entries.find { it.name == name }
        } ?: AuthState.NONE
    }

    /**
     * Atomically writes [state] to the DataStore, serialized as [AuthState.name].
     *
     * @param state The [AuthState] to persist.
     */
    override suspend fun set(state: AuthState) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTH_STATE] = state.name
        }
    }
}
