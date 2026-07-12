package com.mototracker.data.bluetooth

/**
 * A nearby rider detected via a BLE advertisement scan.
 *
 * Created from a decoded [WavePayload] plus the BLE signal metadata reported by the
 * OS scan callback. Passed through [WaveDedupTracker] before being emitted on
 * [BleWaveSource.discoveries].
 *
 * @param shortId          4-character stable device identifier from the sender's payload.
 * @param nick             Rider display name from the payload.
 * @param bike             Motorcycle name from the payload.
 * @param rssiDbm          Received signal strength in dBm (negative value; higher = stronger).
 * @param elapsedRealtimeMs [android.os.SystemClock.elapsedRealtime] timestamp of the scan result.
 */
data class DiscoveredRider(
    val shortId: String,
    val nick: String,
    val bike: String,
    val rssiDbm: Int,
    val elapsedRealtimeMs: Long,
)
