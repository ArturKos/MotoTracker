package com.mototracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mototracker.R
import com.mototracker.data.bluetooth.BleWaveSource
import com.mototracker.data.bluetooth.WaveFactory
import com.mototracker.data.bluetooth.WavePayload
import com.mototracker.data.local.dao.WaveDao
import com.mototracker.data.model.mapper.toEntity
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.settings.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Foreground location service that keeps the GPS lock alive while the app is backgrounded.
 *
 * Started by [com.mototracker.ui.screens.record.RecordingScreen] when recording begins
 * and stopped when the session finishes or is abandoned.
 *
 * B21 wiring: while the service is running, the BLE advertiser broadcasts the rider's
 * own [WavePayload] and the scanner collects [com.mototracker.data.bluetooth.DiscoveredRider]
 * events which are mapped to [com.mototracker.data.model.Wave] rows via [WaveFactory] and
 * upserted into [WaveDao], surfacing through the existing [com.mototracker.data.repository.WaveRepository].
 *
 * On-device behaviour only (🔬) — the service is not invoked in unit tests because GPS,
 * FusedLocationProvider, BLE radio, and foreground-service lifecycle are device-dependent.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var bleWaveSource: BleWaveSource
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var bikeRepository: BikeRepository
    @Inject lateinit var waveDao: WaveDao
    @Inject lateinit var dataStore: DataStore<Preferences>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Route UUID received from the intent; wires BLE-discovered waves to the active route. */
    @Volatile private var activeRouteId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeRouteId = intent?.getStringExtra(EXTRA_ROUTE_ID)
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.screen_record))
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        startBleWaves()

        return START_STICKY
    }

    override fun onDestroy() {
        bleWaveSource.stop()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startBleWaves() {
        serviceScope.launch {
            val shortId = getOrCreateShortId()
            val settings = settingsStore.settings.first()
            if (!settings.wavesEnabled) return@launch
            val bikes = bikeRepository.observeAll().first()
            val bikeName = bikes.firstOrNull { it.id == settings.currentBikeId }?.name ?: ""
            val payload = WavePayload(shortId = shortId, nick = settings.bcName, bike = bikeName)
            bleWaveSource.start(payload)

            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            bleWaveSource.discoveries.collect { rider ->
                val wave = WaveFactory.toWave(
                    rider = rider,
                    place = "",
                    timeLabel = timeFmt.format(Date()),
                    routeId = activeRouteId,
                    id = UUID.randomUUID().toString(),
                )
                waveDao.upsert(wave.toEntity())
            }
        }
    }

    /** Reads the persisted short device id, or generates and stores a new one. */
    private suspend fun getOrCreateShortId(): String {
        val existing = dataStore.data.first()[BLE_DEVICE_ID_KEY]
        if (!existing.isNullOrEmpty()) return existing
        val newId = UUID.randomUUID().toString()
            .filter { it.isLetterOrDigit() }
            .uppercase()
            .take(4)
        dataStore.edit { it[BLE_DEVICE_ID_KEY] = newId }
        return newId
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001
        /** Intent extra key carrying the active route UUID for BLE wave association. */
        const val EXTRA_ROUTE_ID = "extra_route_id"
        private val BLE_DEVICE_ID_KEY = stringPreferencesKey("ble_short_device_id")
    }
}
