package com.mototracker.domain.recording

/**
 * The location technology that produced a [LocationSample].
 *
 * Used by [com.mototracker.data.location.LocationFusion] to prefer precise GPS fixes
 * over coarse network fixes when both are available.
 */
enum class LocationProvider {
    /** High-accuracy satellite fix from [android.location.LocationManager.GPS_PROVIDER]. */
    GPS,

    /**
     * Coarse position derived from cell towers and Wi-Fi via
     * [android.location.LocationManager.NETWORK_PROVIDER].
     *
     * Carries a large [LocationSample.accuracyM] radius; the N2 accuracy gate in
     * `RecordingEngine` will reject these fixes from the recorded odometer/track.
     * Their primary use is the Idle "waiting for fix" readout before GPS locks.
     */
    NETWORK,
}
