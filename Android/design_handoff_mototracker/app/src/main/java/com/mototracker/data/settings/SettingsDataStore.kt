package com.mototracker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        // New canonical keys (U1)
        val NO_INTERNET = booleanPreferencesKey("no_internet")
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        // Legacy keys — kept read-only for migration; no longer written
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
        val CURRENCY = stringPreferencesKey("currency")
        val WAVES_ENABLED = booleanPreferencesKey("waves_enabled")
        val BATTERY_PROMPT_DISMISSED = booleanPreferencesKey("battery_prompt_dismissed")
        val COORD_FORMAT = stringPreferencesKey("coord_format")
        val ENCOUNTER_GAP_MINUTES = intPreferencesKey("encounter_gap_minutes")
        val GROUP_TREATED_SEPARATELY = booleanPreferencesKey("group_treated_separately")
    }

    private val defaults = AppSettings()

    /** Live stream of the current [AppSettings], emitting on every change. */
    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            // Migration: prefer new key; fall back to legacy offline_only key, then default false.
            noInternet = prefs[Keys.NO_INTERNET] ?: prefs[Keys.OFFLINE_ONLY] ?: false,
            // Migration: prefer new key; fall back to (autoSync ?: true) && !(offline ?: false).
            syncEnabled = prefs[Keys.SYNC_ENABLED]
                ?: ((prefs[Keys.AUTO_SYNC] ?: true) && !(prefs[Keys.OFFLINE] ?: false)),
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
            currency = prefs[Keys.CURRENCY] ?: defaults.currency,
            wavesEnabled = prefs[Keys.WAVES_ENABLED] ?: defaults.wavesEnabled,
            batteryPromptDismissed = prefs[Keys.BATTERY_PROMPT_DISMISSED] ?: defaults.batteryPromptDismissed,
            coordFormat = prefs[Keys.COORD_FORMAT] ?: defaults.coordFormat,
            encounterGapMinutes = prefs[Keys.ENCOUNTER_GAP_MINUTES] ?: defaults.encounterGapMinutes,
            groupTreatedSeparately = prefs[Keys.GROUP_TREATED_SEPARATELY] ?: defaults.groupTreatedSeparately,
        )
    }

    /** Persists the master network kill-switch [noInternet] flag (U1). */
    override suspend fun setNoInternet(value: Boolean) {
        dataStore.edit { it[Keys.NO_INTERNET] = value }
    }

    /** Persists the [syncEnabled] flag (U1). */
    override suspend fun setSyncEnabled(value: Boolean) {
        dataStore.edit { it[Keys.SYNC_ENABLED] = value }
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

    /** Persists the ISO 4217 currency code used for fuel cost display. */
    override suspend fun setCurrency(currency: String) {
        dataStore.edit { it[Keys.CURRENCY] = currency }
    }

    /** Persists the BLE waves (pomachania) enabled flag. */
    override suspend fun setWavesEnabled(value: Boolean) {
        dataStore.edit { it[Keys.WAVES_ENABLED] = value }
    }

    /** Persists the battery-optimization prompt dismissed flag (O1). */
    override suspend fun setBatteryPromptDismissed(value: Boolean) {
        dataStore.edit { it[Keys.BATTERY_PROMPT_DISMISSED] = value }
    }

    /** Persists the coordinate display format (P2): "dd", "dms", or "utm". */
    override suspend fun setCoordFormat(value: String) {
        dataStore.edit { it[Keys.COORD_FORMAT] = value }
    }

    /** Persists the encounter-gap threshold in minutes (X1). */
    override suspend fun setEncounterGapMinutes(value: Int) {
        dataStore.edit { it[Keys.ENCOUNTER_GAP_MINUTES] = value }
    }

    /** Persists the group-treated-separately master toggle (X2). */
    override suspend fun setGroupTreatedSeparately(value: Boolean) {
        dataStore.edit { it[Keys.GROUP_TREATED_SEPARATELY] = value }
    }
}
