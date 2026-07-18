package com.mototracker.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mototracker.R
import com.mototracker.core.sms.SmsLocationMessageBuilder
import com.mototracker.core.sms.SmsSendScheduler
import com.mototracker.data.bluetooth.BleWaveSource
import com.mototracker.data.bluetooth.EncounterEvent
import com.mototracker.data.bluetooth.EncounterGap
import com.mototracker.data.bluetooth.EncounterTracker
import com.mototracker.data.bluetooth.WaveSignalDecision
import com.mototracker.data.bluetooth.WaveFactory
import com.mototracker.data.bluetooth.WavePayload
import com.mototracker.data.local.dao.RiderDao
import com.mototracker.data.local.dao.WaveDao
import com.mototracker.data.location.RideLocationCollector
import com.mototracker.data.model.mapper.toEntity
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.settings.SettingsStore
import com.mototracker.data.sms.SmsSender
import com.mototracker.domain.recording.LocationSample
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
 * X1 wiring: instantiates one [EncounterTracker] per session. On each raw BLE sighting
 * the service determines the rider's gap threshold (infinite for in-group members, otherwise
 * [AppSettings.encounterGapMinutes] × 60 000 ms), upserts the rider into [RiderDao], and
 * calls [EncounterTracker.onSighting]. A [EncounterEvent.Started] result opens a new [Wave]
 * row; [EncounterEvent.Extended] updates only [WaveDao.updateLastSeen].
 *
 * All timestamps use [System.currentTimeMillis] (wall clock) for consistent gap arithmetic
 * and persistence — elapsed-realtime would drift relative to persisted epoch values.
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
    @Inject lateinit var riderDao: RiderDao
    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var rideLocationCollector: RideLocationCollector
    @Inject lateinit var rideSignaler: RideSignaler
    @Inject lateinit var smsSender: SmsSender

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Route UUID received from the intent; wires BLE-discovered waves to the active route. */
    @Volatile private var activeRouteId: String? = null

    /** Most recent GPS sample; updated by the location-collector coroutine. */
    @Volatile private var lastSample: LocationSample? = null

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeRouteId = intent?.getStringExtra(EXTRA_ROUTE_ID)
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.screen_record))
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        if (wakeLock?.isHeld != true) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MotoTracker::Recording")
            wakeLock?.acquire()
        }

        rideLocationCollector.start()
        startLocationSampleCollector()
        startSmsLoop()
        startBleWaves()

        return START_STICKY
    }

    override fun onDestroy() {
        rideLocationCollector.stop()
        bleWaveSource.stop()
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** Keeps [lastSample] up-to-date with the latest GPS fix. */
    private fun startLocationSampleCollector() {
        serviceScope.launch {
            rideLocationCollector.samples.collect { sample ->
                lastSample = sample
            }
        }
    }

    /**
     * Periodic loop that sends an SMS location update to configured recipients when all
     * conditions in [SmsSendScheduler.shouldSend] are met.
     *
     * Runs on [Dispatchers.IO] inside [serviceScope] so it stops automatically when
     * [onDestroy] cancels the scope.  The tick period is re-read from settings each iteration
     * so a settings change takes effect on the next tick.
     */
    private fun startSmsLoop() {
        serviceScope.launch(Dispatchers.IO) {
            var lastSentMs: Long? = null
            while (true) {
                val settings = settingsStore.settings.first()
                val intervalMinutes = settings.smsIntervalMinutes
                delay(intervalMinutes.coerceAtLeast(1) * 60_000L)

                val nowMs = System.currentTimeMillis()
                val currentSettings = settingsStore.settings.first()
                val sample = lastSample

                if (SmsSendScheduler.shouldSend(
                        enabled = currentSettings.smsShareEnabled,
                        recipientCount = currentSettings.smsRecipients.size,
                        hasFix = sample != null,
                        lastSentMs = lastSentMs,
                        nowMs = nowMs,
                        intervalMinutes = currentSettings.smsIntervalMinutes,
                    )
                ) {
                    val messages = SmsLocationMessageBuilder.build(
                        recipients = currentSettings.smsRecipients,
                        lat = sample!!.lat,
                        lng = sample.lng,
                        template = getString(R.string.sms_default_template),
                    )
                    messages.forEach { smsSender.send(it) }
                    lastSentMs = nowMs
                }
            }
        }
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

            val encounterTracker = EncounterTracker()
            // shortId → active wave row UUID for this encounter
            val activeWaveIds = mutableMapOf<String, String>()
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

            bleWaveSource.discoveries.collect { rider ->
                val nowMs = System.currentTimeMillis()
                val currentSettings = settingsStore.settings.first()
                val inGroup = riderDao.get(rider.shortId)?.inGroup ?: false
                val gapMs = EncounterGap.resolve(
                    isInGroup = inGroup,
                    groupTreatedSeparately = currentSettings.groupTreatedSeparately,
                    encounterGapMinutes = currentSettings.encounterGapMinutes,
                )

                riderDao.upsertSighting(
                    shortId = rider.shortId,
                    nick = rider.nick,
                    bike = rider.bike,
                    lastSeenMs = nowMs,
                )

                when (val event = encounterTracker.onSighting(rider.shortId, nowMs, gapMs)) {
                    is EncounterEvent.Started -> {
                        val waveId = UUID.randomUUID().toString()
                        activeWaveIds[rider.shortId] = waveId
                        val wave = WaveFactory.toWave(
                            rider = rider,
                            place = "",
                            timeLabel = timeFmt.format(Date(nowMs)),
                            routeId = activeRouteId,
                            id = waveId,
                            firstSeenMs = event.atMs,
                            lastSeenMs = event.atMs,
                        )
                        waveDao.upsert(wave.toEntity())
                        if (WaveSignalDecision.shouldSignal(event, currentSettings.signalWavesEnabled)) {
                            rideSignaler.signal()
                        }
                    }
                    is EncounterEvent.Extended -> {
                        val waveId = activeWaveIds[rider.shortId]
                        if (waveId != null) {
                            waveDao.updateLastSeen(waveId, event.atMs)
                        }
                    }
                }
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
