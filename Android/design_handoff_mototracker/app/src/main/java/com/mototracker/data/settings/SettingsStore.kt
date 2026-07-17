package com.mototracker.data.settings

/**
 * Full read-write contract for [AppSettings] persistence.
 *
 * Extends [WritableSettingsSource] with all the additional setters needed by the
 * Settings screen. The concrete implementation is [SettingsDataStore]; tests may
 * supply any [SettingsStore] fake without depending on DataStore / Android context.
 */
interface SettingsStore : WritableSettingsSource {

    /** Persists the [offline] flag. */
    suspend fun setOffline(value: Boolean)

    /** Persists the [autoSync] flag. */
    suspend fun setAutoSync(value: Boolean)

    /** Persists the [offlineOnly] flag. */
    suspend fun setOfflineOnly(value: Boolean)

    /** Persists the [gpsCorrect] flag. */
    suspend fun setGpsCorrect(value: Boolean)

    /**
     * Persists the selected bike ID, or clears it when [bikeId] is null.
     *
     * @param bikeId UUID of the selected bike, or null to deselect.
     */
    suspend fun setCurrentBikeId(bikeId: String?)

    /** Persists the measurement system ("metric" or "imperial"). */
    suspend fun setUnits(units: String)

    /** Persists the visual theme key ("cockpit", "grid", or "light"). */
    suspend fun setTheme(theme: String)

    /** Persists the accent colour as a hex string. */
    suspend fun setAccent(accent: String)

    /** Persists the BCP-47 language tag. */
    suspend fun setLang(lang: String)

    /** Persists the auto-pause preference. */
    suspend fun setAutoPause(value: Boolean)

    /** Persists the keep-screen-on preference. */
    suspend fun setKeepScreenOn(value: Boolean)

    /** Persists the Android Auto enabled flag. */
    suspend fun setAndroidAutoEnabled(value: Boolean)

    /** Persists the broadcast profile name/handle. */
    suspend fun setBcName(name: String)

    /** Persists the broadcast profile phone number. */
    suspend fun setBcPhone(phone: String)

    /** Persists the broadcast profile origin city. */
    suspend fun setBcOrigin(origin: String)

    /** Persists the broadcast profile social media handle. */
    suspend fun setBcSocial(social: String)

    /** Persists the diagnostic ride-logging enabled flag. */
    suspend fun setDebugLoggingEnabled(value: Boolean)

    /**
     * Persists the OSRM map-matching base URL.
     *
     * Default implementation is a no-op; override in [SettingsDataStore] for real persistence.
     * Test fakes that implement [SettingsStore] are not required to override this until
     * the B13 Settings-screen UI wires it up.
     */
    suspend fun setOsrmBaseUrl(url: String) {}

    /**
     * Persists the ISO 4217 currency code used for fuel cost display, e.g. "PLN" or "EUR".
     *
     * Default implementation is a no-op; override in [SettingsDataStore] for real persistence.
     */
    suspend fun setCurrency(currency: String) {}

    /**
     * Persists the BLE waves (pomachania) enabled flag.
     *
     * Default implementation is a no-op; override in [SettingsDataStore] for real persistence.
     * Test fakes that implement [SettingsStore] are not required to override this.
     */
    suspend fun setWavesEnabled(value: Boolean) {}

    /**
     * Persists the battery-optimization prompt dismissed flag (O1).
     *
     * Default implementation is a no-op; override in [SettingsDataStore] for real persistence.
     * Test fakes that implement [SettingsStore] are not required to override this.
     */
    suspend fun setBatteryPromptDismissed(value: Boolean) {}
}
