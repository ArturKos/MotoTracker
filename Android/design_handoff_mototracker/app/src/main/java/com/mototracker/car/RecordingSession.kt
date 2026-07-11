package com.mototracker.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * Car App Library [Session] that owns the [RecordingCarScreen] for the duration
 * of a head-unit connection (🔬 — requires a physical or emulated head unit).
 *
 * Created by [MotoTrackerCarAppService.onCreateSession]; receives the app-scoped
 * [CarRecordingBridge] so the screen can observe live recording state.
 *
 * @param bridge App-scoped bridge supplied by the [MotoTrackerCarAppService].
 */
class RecordingSession(private val bridge: CarRecordingBridge) : Session() {

    /**
     * Returns the initial [RecordingCarScreen] shown when the head unit connects.
     *
     * Called once per session by the Car App host.
     */
    override fun onCreateScreen(intent: Intent): Screen =
        RecordingCarScreen(carContext, bridge)
}
