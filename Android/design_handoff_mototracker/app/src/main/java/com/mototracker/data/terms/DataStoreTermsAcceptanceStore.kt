package com.mototracker.data.terms

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TermsAcceptanceStore] backed by the app's singleton [DataStore]<[Preferences]>.
 *
 * Reuses the same DataStore provided by [com.mototracker.di.SettingsModule] under
 * a new, non-colliding key (`terms_accepted`). Absent stored values are mapped to
 * `false` (not yet accepted) without throwing.
 *
 * @param dataStore Injected singleton [DataStore]<[Preferences]> instance.
 */
@Singleton
class DataStoreTermsAcceptanceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TermsAcceptanceStore {

    private object Keys {
        val TERMS_ACCEPTED = booleanPreferencesKey("terms_accepted")
    }

    /** Live stream of the current acceptance state, backed by the Preferences DataStore. */
    override val accepted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.TERMS_ACCEPTED] ?: false
    }

    /**
     * Atomically writes [accepted] to the DataStore.
     *
     * @param accepted The acceptance flag to persist.
     */
    override suspend fun setAccepted(accepted: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.TERMS_ACCEPTED] = accepted
        }
    }
}
