package com.mototracker.data.bluetooth

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [BleWaveSource] implementation backed by the Android BLE stack.
 *
 * Uses connectionless BLE advertisement (ADVERTISE_MODE_LOW_LATENCY) so the payload is
 * detectable in the ~1 s window two bikes pass at road speed without a GATT connection.
 * Scanning is filtered to [WavePayloadCodec.SERVICE_UUID] so only MotoTracker peers
 * are decoded.
 *
 * **All BLE calls are wrapped in `try/catch(SecurityException)`** so a missing runtime
 * permission (BLUETOOTH_ADVERTISE / BLUETOOTH_SCAN) never crashes the foreground service.
 *
 * This class is device-only (🔬) — it cannot be meaningfully exercised in unit tests.
 * Use [FakeBleWaveSource] for test doubles.
 *
 * @param context Application context used to obtain the [BluetoothManager].
 */
@Singleton
class AndroidBleWaveSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleWaveSource {

    private val _discoveries = MutableSharedFlow<DiscoveredRider>(extraBufferCapacity = 64)
    override val discoveries: Flow<DiscoveredRider> = _discoveries.asSharedFlow()

    private val dedup = WaveDedupTracker()
    private val serviceParcelUuid = ParcelUuid(WavePayloadCodec.SERVICE_UUID)

    @Volatile private var advertiser: BluetoothLeAdvertiser? = null
    @Volatile private var scanner: BluetoothLeScanner? = null
    @Volatile private var advertiseCallback: AdvertiseCallback? = null
    @Volatile private var scanCallback: ScanCallback? = null

    /**
     * Starts BLE advertising with [self] encoded as service data and begins scanning for
     * other MotoTracker riders advertising the same service UUID.
     *
     * Safe to call when permissions have not yet been granted — all radio calls are
     * guarded by a [SecurityException] catch block.
     *
     * @param self The caller's own rider payload to broadcast.
     */
    override fun start(self: WavePayload) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return

        // ── Advertise ──────────────────────────────────────────────────────────
        val encoded = WavePayloadCodec.encode(self)
        val advData = AdvertiseData.Builder()
            .addServiceData(serviceParcelUuid, encoded)
            .setIncludeDeviceName(false)
            .build()
        val advSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                // Advertise failed — scanning continues so we can still receive waves.
            }
        }
        advertiseCallback = cb
        try {
            advertiser = adapter.bluetoothLeAdvertiser
            advertiser?.startAdvertising(advSettings, advData, cb)
        } catch (_: SecurityException) {
            // BLUETOOTH_ADVERTISE permission not yet granted; scanning may still work.
        }

        // ── Scan ──────────────────────────────────────────────────────────────
        val filter = ScanFilter.Builder()
            .setServiceUuid(serviceParcelUuid)
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanCb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }
        }
        scanCallback = scanCb
        try {
            scanner = adapter.bluetoothLeScanner
            scanner?.startScan(listOf(filter), scanSettings, scanCb)
        } catch (_: SecurityException) {
            // BLUETOOTH_SCAN permission not yet granted.
        }
    }

    /**
     * Stops both the BLE advertiser and the scanner.
     *
     * Safe to call when not started (no-op per platform guarantees).
     */
    override fun stop() {
        try {
            advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: SecurityException) { /* permission revoked after start */ }
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (_: SecurityException) { /* permission revoked after start */ }
        advertiser = null
        scanner = null
        advertiseCallback = null
        scanCallback = null
    }

    /**
     * Returns the device's BLE capability for connectionless wave broadcasts.
     *
     * Wrapped in `try/catch` because checking the adapter state may itself require
     * the BLUETOOTH permission on older APIs.
     */
    override fun capabilities(): BleWaveCapability = try {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        when {
            adapter == null -> BleWaveCapability.BLE_UNAVAILABLE
            !adapter.isMultipleAdvertisementSupported -> BleWaveCapability.ADVERTISE_UNSUPPORTED
            else -> BleWaveCapability.SUPPORTED
        }
    } catch (_: SecurityException) {
        BleWaveCapability.BLE_UNAVAILABLE
    }

    private fun handleScanResult(result: ScanResult) {
        val bytes = result.scanRecord?.getServiceData(serviceParcelUuid) ?: return
        val payload = WavePayloadCodec.decode(bytes) ?: return
        val nowMs = SystemClock.elapsedRealtime()
        if (!dedup.accept(payload.shortId, nowMs)) return
        val rider = DiscoveredRider(
            shortId = payload.shortId,
            nick = payload.nick,
            bike = payload.bike,
            rssiDbm = result.rssi,
            elapsedRealtimeMs = nowMs,
        )
        _discoveries.tryEmit(rider)
    }
}
