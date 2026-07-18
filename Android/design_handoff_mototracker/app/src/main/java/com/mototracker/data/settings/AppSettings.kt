package com.mototracker.data.settings

/**
 * Immutable snapshot of all persisted application settings.
 *
 * Mirrors the settings fields from README §State (lines 141–142).
 * Defaults match the prototype initial state.
 *
 * @param noInternet          Master network kill switch: when `true` ALL outbound network
 *                            activity is blocked (weather, sync, GPS correction, online group).
 *                            Replaces the old `offlineOnly` flag. Migration: new value is derived
 *                            from `no_internet` key; falls back to legacy `offline_only` key.
 * @param syncEnabled         Whether routes should be uploaded to the server. When `false`,
 *                            no uploads occur; forced `false` while [noInternet] is `true`.
 *                            Replaces the old `offline`/`autoSync` pair. Migration: new value is
 *                            derived from `sync_enabled` key; falls back to
 *                            `(autoSync ?: true) && !(offline ?: false)` from legacy keys.
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
 * @param wavesEnabled        Whether the BLE "waves" (pomachania) feature is active.
 *                            When `false`, [com.mototracker.service.RecordingService] skips
 *                            BLE advertise/scan and the Riders waves section is hidden.
 *                            Defaults to `true` to preserve the existing always-on behaviour.
 * @param batteryPromptDismissed Whether the user has dismissed the battery-optimization exemption
 *                               prompt (O1). When `true`, the prompt is never shown again even if
 *                               the app is not yet exempt.
 * @param coordFormat            Coordinate display format: "dd" (decimal degrees), "dms"
 *                               (degrees/minutes/seconds), or "utm". Defaults to "dd".
 * @param encounterGapMinutes    Minutes of radio silence that splits one encounter into two.
 *                               For in-group riders this is effectively infinite (handled in
 *                               [com.mototracker.service.RecordingService]). Defaults to 10.
 */
data class AppSettings(
    val noInternet: Boolean = false,
    val syncEnabled: Boolean = true,
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
    val wavesEnabled: Boolean = true,
    val batteryPromptDismissed: Boolean = false,
    val coordFormat: String = "dd",
    val encounterGapMinutes: Int = 10,
)
