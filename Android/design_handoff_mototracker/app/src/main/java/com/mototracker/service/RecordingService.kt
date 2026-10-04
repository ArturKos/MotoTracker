package com.mototracker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.mototracker.ui.screens.record.ActiveRideHolder
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
 * Started and stopped by [com.mototracker.ui.screens.record.ActiveRide] as the ride enters
 * Recording and returns to Idle. Its notification opens the app and offers "Finish & save"
 * ([ACTION_FINISH]). If Android refuses the foreground state, the ride is paused via
 * [com.mototracker.ui.screens.record.ActiveRide.onRecordingServiceBlocked].
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
    @Inject lateinit var rideHolder: ActiveRideHolder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Route UUID received from the intent; wires BLE-discovered waves to the active route. */
    @Volatile private var activeRouteId: String? = null

    /** Most recent GPS sample; updated by the location-collector coroutine. */
    @Volatile private var lastSample: LocationSample? = null

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Guards the per-session workers (location collector, SMS loop, BLE waves) so they are
     * launched exactly once per service instance. [onStartCommand] runs again on every
     * `startForegroundService` call — each Paused→Recording transition and each return to the
     * Record screen — and previously spawned a duplicate SMS loop and BLE collector each time.
     */
    private val workersStarted = StartOnceLatch()

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // START_STICKY restart after the process was killed: the recording engine lives in
            // the UI process state and is gone, so nothing would be recorded. Don't keep GPS,
            // BLE and a wakelock alive for nothing — the rider resumes from the saved snapshot.
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_FINISH) {
            // "Finish & save" from the notification: the ride saves itself and, once Idle,
            // stops this service through its phase hook.
            val ride = rideHolder.ride
            if (ride == null) stopSelf(startId) else ride.finishAsync()
            return START_NOT_STICKY
        }
        intent.getStringExtra(EXTRA_ROUTE_ID)?.let { activeRouteId = it }
        ensureChannel()
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            // Android 12+ refuses a foreground service started from the background, and
            // Android 14 refuses a location one without a while-in-use grant (e.g. a ride
            // started from Android Auto with the phone locked). Without the foreground state
            // there is no GPS, so pause the ride and tell the rider instead of crashing.
            rideHolder.ride?.onRecordingServiceBlocked()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (wakeLock?.isHeld != true) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MotoTracker::Recording")
            wakeLock?.acquire()
        }

        if (workersStarted.tryAcquire()) {
            rideLocationCollector.start()
            startLocationSampleCollector()
            startSmsLoop()
            startBleWaves()
        }

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
                        fixTimeMs = sample?.timeMs,
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

    /**
     * Ongoing ride notification: tapping it opens the app, and its action finishes and saves
     * the ride without unlocking into the app.
     */
    private fun buildNotification(): Notification {
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN_APP,
                launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val finish = PendingIntent.getService(
            this,
            REQUEST_FINISH,
            Intent(this, RecordingService::class.java).setAction(ACTION_FINISH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.screen_record))
            .setContentText(getString(R.string.notif_recording_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notif_action_finish), finish)
            .setOngoing(true)
            .build()
    }

    /** Creates the channel, or renames an existing one to the current language. */
    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_recording),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001
        /** Intent extra key carrying the active route UUID for BLE wave association. */
        const val EXTRA_ROUTE_ID = "extra_route_id"
        /** Intent action sent by the notification's "Finish & save" button. */
        const val ACTION_FINISH = "com.mototracker.action.FINISH_RIDE"
        private const val REQUEST_OPEN_APP = 1
        private const val REQUEST_FINISH = 2
        private val BLE_DEVICE_ID_KEY = stringPreferencesKey("ble_short_device_id")
    }
}
