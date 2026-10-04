package com.mototracker.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.mototracker.data.repository.SyncAutoStarter
import dagger.hilt.android.AndroidEntryPoint
import com.mototracker.ui.screens.record.ActiveRideHolder
import javax.inject.Inject

/**
 * Car App Library service entry point for the Android Auto head-unit integration
 * (🔬 — requires a physical or emulated Android Auto head unit for runtime verification).
 *
 * Registered in [AndroidManifest.xml] with the `androidx.car.app.CarAppService` intent
 * action and `androidx.car.app.category.IOT` category so the Android Auto host can bind
 * to it when the user opens MotoTracker on a connected head unit.
 *
 * [HostValidator.ALLOW_ALL_HOSTS_VALIDATOR] is intentionally used here because this is a
 * personal/sideloaded app that is not distributed via the Play Store; strict host validation
 * would reject self-signed debug builds.
 */
@AndroidEntryPoint
class MotoTrackerCarAppService : CarAppService() {

    @Inject
    lateinit var bridge: CarRecordingBridge

    /** Ensures the process ride exists so car commands work even if the phone UI never opened. */
    @Inject
    lateinit var rideHolder: ActiveRideHolder

    /** Uploads routes finished from the car even when the phone UI never opened this process. */
    @Inject
    lateinit var syncAutoStarter: SyncAutoStarter

    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    /**
     * Creates a new [RecordingSession] for each head-unit connection, passing the
     * app-scoped [CarRecordingBridge] so the car screen can observe recording state.
     */
    override fun onCreateSession(): Session {
        rideHolder.obtainOrNull()
        syncAutoStarter.ensureStarted()
        return RecordingSession(bridge)
    }
}
