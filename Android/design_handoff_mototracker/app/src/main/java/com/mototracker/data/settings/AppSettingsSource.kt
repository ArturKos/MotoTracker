package com.mototracker.data.settings

import kotlinx.coroutines.flow.Flow

/**
 * Read-only view of the persisted application settings.
 *
 * This interface exists so that the sync layer (and its unit tests) can depend on a
 * settings source without pulling in the DataStore/Context machinery of [SettingsDataStore].
 */
interface AppSettingsSource {
    /** Live stream of the current [AppSettings]; emits on every change. */
    val settings: Flow<AppSettings>
}
