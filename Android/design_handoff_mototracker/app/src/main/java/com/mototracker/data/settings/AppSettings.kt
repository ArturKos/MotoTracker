package com.mototracker.data.settings

/**
 * Immutable snapshot of all persisted application settings.
 *
 * Mirrors the settings fields from README §State (lines 141–142).
 * Defaults match the prototype initial state.
 *
 * @param offline             Whether the app is in explicit offline mode.
 * @param autoSync            Whether routes should be uploaded automatically when online.
 * @param offlineOnly         Whether the user has disabled all network activity.
 * @param gpsCorrect          Whether GPS road-correction (map-matching) is enabled.
 * @param currentBikeId       ID of the currently selected bike; null if none selected.
 * @param serverAddress       Base URL of the GPStrack server.
 * @param units               Measurement system: "metric" or "imperial".
 * @param theme               Visual theme key: "cockpit", "grid", or "light".
 * @param accent              Accent colour as a hex string, e.g. "#00BCD4".
 * @param lang                BCP-47 language tag, e.g. "pl" or "en".
 * @param autoPause           Whether to auto-pause recording when the bike is stationary.
 * @param keepScreenOn        Whether the display stays on during a ride.
 * @param androidAutoEnabled  Whether the Android Auto car-display integration is enabled.
 * @param bcName              Bluetooth broadcast: rider name or handle.
 * @param bcPhone             Bluetooth broadcast: rider phone number.
 * @param bcOrigin            Bluetooth broadcast: riding-from city.
 * @param bcSocial            Bluetooth broadcast: social media handle.
 * @param debugLoggingEnabled Whether diagnostic ride logs are written to external storage.
 *                            Defaults to `false`; toggled by the B10 diagnostics UI.
 * @param osrmBaseUrl         Base URL of the OSRM map-matching instance used for GPS road-correction.
 *                            No Settings-screen UI yet (B13); persisted for A10 infrastructure.
 * @param currency            ISO 4217 currency code used for fuel cost display, e.g. "PLN" or "EUR".
 */
data class AppSettings(
    val offline: Boolean = false,
    val autoSync: Boolean = true,
    val offlineOnly: Boolean = false,
    val gpsCorrect: Boolean = true,
    val currentBikeId: String? = null,
    val serverAddress: String = "http://192.168.1.145/gpstrack",
    val units: String = "metric",
    val theme: String = "cockpit",
    val accent: String = "#00D1B2",
    val lang: String = "pl",
    val autoPause: Boolean = true,
    val keepScreenOn: Boolean = false,
    val androidAutoEnabled: Boolean = false,
    val bcName: String = "",
    val bcPhone: String = "",
    val bcOrigin: String = "",
    val bcSocial: String = "",
    val debugLoggingEnabled: Boolean = false,
    val osrmBaseUrl: String = "http://192.168.1.142:5001",
    val currency: String = "PLN",
)
