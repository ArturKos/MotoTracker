package com.mototracker.data.settings

/**
 * Extension of [AppSettingsSource] that also permits persisting the server address.
 *
 * Provides the minimal write surface needed by [com.mototracker.ui.screens.login.LoginViewModel]
 * without exposing the full [SettingsDataStore] API to the UI layer.
 */
interface WritableSettingsSource : AppSettingsSource {

    /** Persists the GPStrack server base URL. */
    suspend fun setServerAddress(address: String)
}
