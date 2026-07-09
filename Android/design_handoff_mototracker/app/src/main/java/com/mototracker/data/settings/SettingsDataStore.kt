package com.mototracker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [DataStore]<[Preferences]> exposing typed [AppSettings]
 * and individual suspend setters.
 *
 * The underlying [DataStore] is provided and managed as a singleton by
 * [com.mototracker.di.SettingsModule]; this class must therefore also be
 * annotated [@Singleton][Singleton] so exactly one DataStore file is open at a time.
 *
 * All reads go through [settings]; all writes are suspend functions that call
 * [DataStore.edit] on the IO-safe DataStore coroutine context.
 *
 * @param dataStore Injected singleton [DataStore]<[Preferences]> instance.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : WritableSettingsSource {

    private object Keys {
        val OFFLINE = booleanPreferencesKey("offline")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val OFFLINE_ONLY = booleanPreferencesKey("offline_only")
        val GPS_CORRECT = booleanPreferencesKey("gps_correct")
        val CURRENT_BIKE_ID = stringPreferencesKey("current_bike_id")
        val SERVER_ADDRESS = stringPreferencesKey("server_address")
        val UNITS = stringPreferencesKey("units")
        val THEME = stringPreferencesKey("theme")
        val ACCENT = stringPreferencesKey("accent")
        val LANG = stringPreferencesKey("lang")
    }

    private val defaults = AppSettings()

    /** Live stream of the current [AppSettings], emitting on every change. */
    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            offline = prefs[Keys.OFFLINE] ?: defaults.offline,
            autoSync = prefs[Keys.AUTO_SYNC] ?: defaults.autoSync,
            offlineOnly = prefs[Keys.OFFLINE_ONLY] ?: defaults.offlineOnly,
            gpsCorrect = prefs[Keys.GPS_CORRECT] ?: defaults.gpsCorrect,
            currentBikeId = prefs[Keys.CURRENT_BIKE_ID],
            serverAddress = prefs[Keys.SERVER_ADDRESS] ?: defaults.serverAddress,
            units = prefs[Keys.UNITS] ?: defaults.units,
            theme = prefs[Keys.THEME] ?: defaults.theme,
            accent = prefs[Keys.ACCENT] ?: defaults.accent,
            lang = prefs[Keys.LANG] ?: defaults.lang,
        )
    }

    /** Persists the [offline] flag. */
    suspend fun setOffline(value: Boolean) {
        dataStore.edit { it[Keys.OFFLINE] = value }
    }

    /** Persists the [autoSync] flag. */
    suspend fun setAutoSync(value: Boolean) {
        dataStore.edit { it[Keys.AUTO_SYNC] = value }
    }

    /** Persists the [offlineOnly] flag. */
    suspend fun setOfflineOnly(value: Boolean) {
        dataStore.edit { it[Keys.OFFLINE_ONLY] = value }
    }

    /** Persists the [gpsCorrect] flag. */
    suspend fun setGpsCorrect(value: Boolean) {
        dataStore.edit { it[Keys.GPS_CORRECT] = value }
    }

    /**
     * Persists the selected bike ID, or clears it if [bikeId] is null.
     *
     * @param bikeId UUID of the selected bike, or null to deselect.
     */
    suspend fun setCurrentBikeId(bikeId: String?) {
        dataStore.edit { prefs ->
            if (bikeId != null) prefs[Keys.CURRENT_BIKE_ID] = bikeId
            else prefs.remove(Keys.CURRENT_BIKE_ID)
        }
    }

    /** Persists the GPStrack server base URL. */
    override suspend fun setServerAddress(address: String) {
        dataStore.edit { it[Keys.SERVER_ADDRESS] = address }
    }

    /** Persists the measurement system ("metric" or "imperial"). */
    suspend fun setUnits(units: String) {
        dataStore.edit { it[Keys.UNITS] = units }
    }

    /** Persists the visual theme key ("cockpit", "grid", or "light"). */
    suspend fun setTheme(theme: String) {
        dataStore.edit { it[Keys.THEME] = theme }
    }

    /** Persists the accent colour as a hex string. */
    suspend fun setAccent(accent: String) {
        dataStore.edit { it[Keys.ACCENT] = accent }
    }

    /** Persists the BCP-47 language tag. */
    suspend fun setLang(lang: String) {
        dataStore.edit { it[Keys.LANG] = lang }
    }
}
