package com.mototracker.data.bluetooth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Hardware capability of the device's BLE radio for connectionless wave broadcasts.
 */
enum class BleWaveCapability {
    /** Adapter is available and the chipset supports concurrent advertise + scan. */
    SUPPORTED,

    /** Adapter is available but [android.bluetooth.BluetoothAdapter.isMultipleAdvertisementSupported]
     *  returned false; scanning-only mode may still work. */
    ADVERTISE_UNSUPPORTED,

    /** Bluetooth adapter is absent or disabled. */
    BLE_UNAVAILABLE,
}

/**
 * Seam between the recording layer and the BLE radio for connectionless rider-wave broadcasts.
 *
 * The production implementation is [AndroidBleWaveSource]. Tests use [FakeBleWaveSource] or
 * any custom implementation.
 *
 * All BLE calls are expected to degrade gracefully when permissions have not been granted;
 * see [capabilities] for a pre-flight capability check.
 */
interface BleWaveSource {

    /** Cold/hot stream of [DiscoveredRider] objects that passed the [WaveDedupTracker] gate. */
    val discoveries: Flow<DiscoveredRider>

    /**
     * Starts BLE advertising (self payload) and scanning (for other MotoTracker riders).
     *
     * No-op if called while already started. Safe to call before permissions are granted —
     * the implementation wraps radio calls in `try/catch SecurityException`.
     *
     * @param self The caller's own payload to advertise.
     */
    fun start(self: WavePayload)

    /**
     * Stops both the advertiser and the scanner.
     *
     * Safe to call when not started (no-op).
     */
    fun stop()

    /**
     * Returns the device's BLE capability relevant to connectionless waves.
     *
     * Call this before [start] to decide whether to show a degradation message.
     */
    fun capabilities(): BleWaveCapability
}

// ─────────────────────────────────────────────────────────────────────────────
// Fake for unit tests
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory [BleWaveSource] for use in unit tests.
 *
 * Callers can push [DiscoveredRider] events directly via [emit] and inspect whether
 * [start]/[stop] were called.
 *
 * @param capability The capability value returned by [capabilities]. Defaults to [BleWaveCapability.SUPPORTED].
 */
class FakeBleWaveSource(
    private val capability: BleWaveCapability = BleWaveCapability.SUPPORTED,
) : BleWaveSource {

    private val _discoveries = MutableSharedFlow<DiscoveredRider>(extraBufferCapacity = 64)
    override val discoveries: Flow<DiscoveredRider> = _discoveries.asSharedFlow()

    /** The most recent payload passed to [start], or null if not yet called. */
    var lastStartPayload: WavePayload? = null
        private set

    /** Number of times [stop] has been called. */
    var stopCount: Int = 0
        private set

    override fun start(self: WavePayload) {
        lastStartPayload = self
    }

    override fun stop() {
        stopCount++
    }

    override fun capabilities(): BleWaveCapability = capability

    /**
     * Pushes a [DiscoveredRider] into [discoveries] so tests can verify downstream behaviour.
     *
     * @param rider The rider to emit.
     */
    fun emit(rider: DiscoveredRider) {
        _discoveries.tryEmit(rider)
    }
}
