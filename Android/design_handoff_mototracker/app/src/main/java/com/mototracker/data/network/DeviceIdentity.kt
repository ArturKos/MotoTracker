package com.mototracker.data.network

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tożsamość tego telefonu jako urządzenia GPStrack.
 *
 * [code] to stabilny identyfikator instalacji (UUID), generowany raz i utrwalany.
 * [name] to czytelna nazwa modelu telefonu wysyłana do serwera przy uploadzie.
 */
interface DeviceIdentity {
    /** Stabilny UUID instalacji (ten sam przy kolejnych wywołaniach). */
    suspend fun code(): String

    /** Nazwa urządzenia, np. "samsung SM-G991B". */
    fun name(): String
}

/**
 * [DeviceIdentity] utrwalający UUID instalacji w singletonowym [DataStore]<[Preferences]>
 * (ten sam store co [DataStoreSessionStore]), pod dedykowanym kluczem `device_install_uuid`.
 *
 * @param dataStore  Singletonowy Preferences DataStore.
 * @param deviceName Nazwa urządzenia; w produkcji z [Build] (patrz NetworkModule).
 */
@Singleton
class DataStoreDeviceIdentity @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val deviceName: String,
) : DeviceIdentity {

    private object Keys {
        val INSTALL_UUID = stringPreferencesKey("device_install_uuid")
    }

    override suspend fun code(): String {
        val prefs = dataStore.edit { p ->
            if (p[Keys.INSTALL_UUID].isNullOrBlank()) {
                p[Keys.INSTALL_UUID] = UUID.randomUUID().toString()
            }
        }
        return prefs[Keys.INSTALL_UUID]!!
    }

    override fun name(): String = deviceName
}
