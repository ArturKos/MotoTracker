package com.mototracker.ui.screens.settings

import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Rider

/**
 * Represents a single motorcycle row in the Settings → My motorcycles section.
 *
 * @param id                      Bike UUID.
 * @param name                    Display name.
 * @param yearPlate               Formatted "year · plate" string, e.g. "2020 · WA 12345".
 * @param status                  Active or sold lifecycle flag.
 * @param isCurrent               Whether this is the user-selected active bike.
 * @param year                    Model year; used to prefill the add/edit dialog.
 * @param plate                   Registration plate; used to prefill the add/edit dialog.
 * @param tankCapacityL           Fuel tank capacity in litres; null when not configured.
 * @param fuelPricePerL           Fuel price per litre; null when not configured.
 * @param consumptionLper100km    Average fuel consumption in L/100km; null when not configured.
 * @param autoUpdateConsumption   Whether the auto-update-from-refuels checkbox is checked (K2).
 */
data class BikeUi(
    val id: String,
    val name: String,
    val yearPlate: String,
    val status: BikeStatus,
    val isCurrent: Boolean,
    val year: Int = 0,
    val plate: String = "",
    val tankCapacityL: Double? = null,
    val fuelPricePerL: Double? = null,
    val consumptionLper100km: Double? = null,
    val autoUpdateConsumption: Boolean = false,
)

/**
 * Represents one unsynced route in the Settings → Sync queue section.
 *
 * @param routeId     Route UUID (used to trigger per-item sync).
 * @param name        Route display name.
 * @param dateDisplay Localised date string, e.g. "12.06.2026".
 * @param kmDisplay   Formatted distance, e.g. "128.4 km".
 */
data class SyncQueueItemUi(
    val routeId: String,
    val name: String,
    val dateDisplay: String,
    val kmDisplay: String,
)

/**
 * Immutable UI state for the Settings screen (B7).
 *
 * Combines persisted [com.mototracker.data.settings.AppSettings] with the live bike
 * list, unsynced route queue, and auto-computed broadcast profile stats. All display
 * values are pre-formatted so the Composable remains a pure renderer.
 *
 * @param bikes              Ordered list of the user's motorcycles.
 * @param currentBikeId      UUID of the active bike; null if none selected.
 * @param theme              Active visual theme key ("cockpit", "grid", or "light").
 * @param accent             Active accent colour hex string.
 * @param language           Active BCP-47 language tag.
 * @param units              Measurement system ("metric" or "imperial").
 * @param serverAddress      GPStrack server base URL.
 * @param noInternet         Master network kill-switch: blocks all outbound traffic (U1).
 * @param syncEnabled        Whether upload to server is enabled; forced off while [noInternet] is on (U1).
 * @param pendingRoutes      Routes not yet synced to the server.
 * @param bcName             Broadcast profile: rider name/handle.
 * @param bcPhone            Broadcast profile: phone number.
 * @param bcOrigin           Broadcast profile: origin city.
 * @param bcSocial           Broadcast profile: social media handle.
 * @param bcBikeDisplay      Auto: current bike name+year (read-only in broadcast section).
 * @param bcTodayDisplay     Auto: km ridden today (read-only in broadcast section).
 * @param bcTotalDisplay     Auto: total km in app (read-only in broadcast section).
 * @param gpsCorrect           System: enable GPS road-correction (map-matching).
 * @param androidAutoEnabled   System: show on car display.
 * @param autoPause            Preference: auto-pause recording when stationary.
 * @param keepScreenOn         Preference: keep display on during a ride.
 * @param debugLoggingEnabled  Diagnostics: write per-ride log files to external storage.
 * @param rideLogUsedBytes     Diagnostics: total bytes consumed by ride-log files in
 *                             external storage; 0 when logging is disabled or no logs exist.
 * @param currency             ISO 4217 currency code used for fuel cost display, e.g. "PLN".
 * @param wavesEnabled         Whether the BLE waves (pomachania) feature is enabled.
 *                             When `false`, the Settings toggle is off and no BLE advertise/scan occurs.
 * @param coordFormat              Coordinate display format key: "dd", "dms", or "utm" (P2).
 * @param osrmBaseUrl              OSRM map-matching server base URL for GPS road-correction (W1);
 *                                 default "http://192.168.1.142:5001".
 * @param groupTreatedSeparately   Master toggle: when `true`, in-group riders get an infinite
 *                                 encounter gap (X2).
 * @param knownRiders              All known BLE-discovered riders, newest-first (X2).
 * @param signalWavesEnabled       When `true`, a short haptic fires on each new BLE encounter
 *                                 during a ride (X3). Encounters are still recorded when `false`.
 */
data class SettingsUiState(
    val bikes: List<BikeUi> = emptyList(),
    val currentBikeId: String? = null,
    val theme: String = "cockpit",
    val accent: String = "#00D1B2",
    val language: String = "pl",
    val units: String = "metric",
    val serverAddress: String = "http://192.168.1.145/gpstrack",
    val noInternet: Boolean = false,
    val syncEnabled: Boolean = true,
    val pendingRoutes: List<SyncQueueItemUi> = emptyList(),
    val bcName: String = "",
    val bcPhone: String = "",
    val bcOrigin: String = "",
    val bcSocial: String = "",
    val bcBikeDisplay: String = "",
    val bcTodayDisplay: String = "",
    val bcTotalDisplay: String = "",
    val gpsCorrect: Boolean = true,
    val androidAutoEnabled: Boolean = false,
    val autoPause: Boolean = true,
    val keepScreenOn: Boolean = false,
    val debugLoggingEnabled: Boolean = false,
    val rideLogUsedBytes: Long = 0L,
    val currency: String = "PLN",
    val wavesEnabled: Boolean = true,
    val coordFormat: String = "dd",
    val osrmBaseUrl: String = "http://192.168.1.142:5001",
    val groupTreatedSeparately: Boolean = true,
    val knownRiders: List<Rider> = emptyList(),
    val signalWavesEnabled: Boolean = true,
)
