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
) : SettingsStore {

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
        val AUTO_PAUSE = booleanPreferencesKey("auto_pause")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val ANDROID_AUTO_ENABLED = booleanPreferencesKey("android_auto_enabled")
        val BC_NAME = stringPreferencesKey("bc_name")
        val BC_PHONE = stringPreferencesKey("bc_phone")
        val BC_ORIGIN = stringPreferencesKey("bc_origin")
        val BC_SOCIAL = stringPreferencesKey("bc_social")
        val DEBUG_LOGGING_ENABLED = booleanPreferencesKey("debug_logging_enabled")
        val OSRM_BASE_URL = stringPreferencesKey("osrm_base_url")
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
            autoPause = prefs[Keys.AUTO_PAUSE] ?: defaults.autoPause,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            androidAutoEnabled = prefs[Keys.ANDROID_AUTO_ENABLED] ?: defaults.androidAutoEnabled,
            bcName = prefs[Keys.BC_NAME] ?: defaults.bcName,
            bcPhone = prefs[Keys.BC_PHONE] ?: defaults.bcPhone,
            bcOrigin = prefs[Keys.BC_ORIGIN] ?: defaults.bcOrigin,
            bcSocial = prefs[Keys.BC_SOCIAL] ?: defaults.bcSocial,
            debugLoggingEnabled = prefs[Keys.DEBUG_LOGGING_ENABLED] ?: defaults.debugLoggingEnabled,
            osrmBaseUrl = prefs[Keys.OSRM_BASE_URL] ?: defaults.osrmBaseUrl,
        )
    }

    /** Persists the [offline] flag. */
    override suspend fun setOffline(value: Boolean) {
        dataStore.edit { it[Keys.OFFLINE] = value }
    }

    /** Persists the [autoSync] flag. */
    override suspend fun setAutoSync(value: Boolean) {
        dataStore.edit { it[Keys.AUTO_SYNC] = value }
    }

    /** Persists the [offlineOnly] flag. */
    override suspend fun setOfflineOnly(value: Boolean) {
        dataStore.edit { it[Keys.OFFLINE_ONLY] = value }
    }

    /** Persists the [gpsCorrect] flag. */
    override suspend fun setGpsCorrect(value: Boolean) {
        dataStore.edit { it[Keys.GPS_CORRECT] = value }
    }

    /**
     * Persists the selected bike ID, or clears it if [bikeId] is null.
     *
     * @param bikeId UUID of the selected bike, or null to deselect.
     */
    override suspend fun setCurrentBikeId(bikeId: String?) {
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
    override suspend fun setUnits(units: String) {
        dataStore.edit { it[Keys.UNITS] = units }
    }

    /** Persists the visual theme key ("cockpit", "grid", or "light"). */
    override suspend fun setTheme(theme: String) {
        dataStore.edit { it[Keys.THEME] = theme }
    }

    /** Persists the accent colour as a hex string. */
    override suspend fun setAccent(accent: String) {
        dataStore.edit { it[Keys.ACCENT] = accent }
    }

    /** Persists the BCP-47 language tag. */
    override suspend fun setLang(lang: String) {
        dataStore.edit { it[Keys.LANG] = lang }
    }

    /** Persists the auto-pause preference. */
    override suspend fun setAutoPause(value: Boolean) {
        dataStore.edit { it[Keys.AUTO_PAUSE] = value }
    }

    /** Persists the keep-screen-on preference. */
    override suspend fun setKeepScreenOn(value: Boolean) {
        dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }
    }

    /** Persists the Android Auto enabled flag. */
    override suspend fun setAndroidAutoEnabled(value: Boolean) {
        dataStore.edit { it[Keys.ANDROID_AUTO_ENABLED] = value }
    }

    /** Persists the broadcast profile name/handle. */
    override suspend fun setBcName(name: String) {
        dataStore.edit { it[Keys.BC_NAME] = name }
    }

    /** Persists the broadcast profile phone number. */
    override suspend fun setBcPhone(phone: String) {
        dataStore.edit { it[Keys.BC_PHONE] = phone }
    }

    /** Persists the broadcast profile origin city. */
    override suspend fun setBcOrigin(origin: String) {
        dataStore.edit { it[Keys.BC_ORIGIN] = origin }
    }

    /** Persists the broadcast profile social media handle. */
    override suspend fun setBcSocial(social: String) {
        dataStore.edit { it[Keys.BC_SOCIAL] = social }
    }

    /** Persists the diagnostic ride-logging enabled flag. */
    override suspend fun setDebugLoggingEnabled(value: Boolean) {
        dataStore.edit { it[Keys.DEBUG_LOGGING_ENABLED] = value }
    }

    /** Persists the OSRM map-matching base URL. */
    override suspend fun setOsrmBaseUrl(url: String) {
        dataStore.edit { it[Keys.OSRM_BASE_URL] = url }
    }
}
