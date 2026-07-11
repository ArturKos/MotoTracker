package com.mototracker.ui.screens.settings

import com.mototracker.data.local.entity.BikeStatus

/**
 * Represents a single motorcycle row in the Settings → My motorcycles section.
 *
 * @param id         Bike UUID.
 * @param name       Display name.
 * @param yearPlate  Formatted "year · plate" string, e.g. "2020 · WA 12345".
 * @param status     Active or sold lifecycle flag.
 * @param isCurrent  Whether this is the user-selected active bike.
 * @param year       Model year; used to prefill the add/edit dialog.
 * @param plate      Registration plate; used to prefill the add/edit dialog.
 */
data class BikeUi(
    val id: String,
    val name: String,
    val yearPlate: String,
    val status: BikeStatus,
    val isCurrent: Boolean,
    val year: Int = 0,
    val plate: String = "",
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
 * @param offline            Offline mode switch state.
 * @param autoSync           Auto-sync switch state.
 * @param pendingRoutes      Routes not yet synced to the server.
 * @param bcName             Broadcast profile: rider name/handle.
 * @param bcPhone            Broadcast profile: phone number.
 * @param bcOrigin           Broadcast profile: origin city.
 * @param bcSocial           Broadcast profile: social media handle.
 * @param bcBikeDisplay      Auto: current bike name+year (read-only in broadcast section).
 * @param bcTodayDisplay     Auto: km ridden today (read-only in broadcast section).
 * @param bcTotalDisplay     Auto: total km in app (read-only in broadcast section).
 * @param offlineOnly          System: disable all network activity.
 * @param gpsCorrect           System: enable GPS road-correction (map-matching).
 * @param androidAutoEnabled   System: show on car display.
 * @param autoPause            Preference: auto-pause recording when stationary.
 * @param keepScreenOn         Preference: keep display on during a ride.
 * @param debugLoggingEnabled  Diagnostics: write per-ride log files to external storage.
 *                             Exposed here so B10 can bind a toggle; no Settings row
 *                             is added in A8.
 */
data class SettingsUiState(
    val bikes: List<BikeUi> = emptyList(),
    val currentBikeId: String? = null,
    val theme: String = "cockpit",
    val accent: String = "#00D1B2",
    val language: String = "pl",
    val units: String = "metric",
    val serverAddress: String = "http://192.168.1.145/gpstrack",
    val offline: Boolean = false,
    val autoSync: Boolean = true,
    val pendingRoutes: List<SyncQueueItemUi> = emptyList(),
    val bcName: String = "",
    val bcPhone: String = "",
    val bcOrigin: String = "",
    val bcSocial: String = "",
    val bcBikeDisplay: String = "",
    val bcTodayDisplay: String = "",
    val bcTotalDisplay: String = "",
    val offlineOnly: Boolean = false,
    val gpsCorrect: Boolean = true,
    val androidAutoEnabled: Boolean = false,
    val autoPause: Boolean = true,
    val keepScreenOn: Boolean = false,
    val debugLoggingEnabled: Boolean = false,
)
