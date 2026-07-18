package com.mototracker.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emits a short signal (haptic / vibration) to alert the rider that a new BLE
 * encounter has started during a ride (X3).
 *
 * The interface is Android-free so tests can provide a no-op fake without
 * requiring a device or Robolectric.
 */
interface RideSignaler {

    /**
     * Fires a brief signal. Implementations must be safe to call from any thread
     * and must never throw — a failing signal must not crash the recording session.
     */
    fun signal()
}

/**
 * [RideSignaler] implementation that emits a short (~150 ms) haptic vibration.
 *
 * Uses [VibratorManager] on API 31+ and falls back to the legacy [Vibrator] on
 * older devices. The vibrate call is wrapped in a try/catch so any hardware or
 * permission failure degrades silently rather than crashing the foreground service.
 *
 * The actual haptic firing on-device is 🔬 (requires a physical device).
 *
 * @param context Application context used to obtain the vibrator service.
 */
@Singleton
class VibratorRideSignaler @Inject constructor(
    @ApplicationContext private val context: Context,
) : RideSignaler {

    override fun signal() {
        try {
            val effect = VibrationEffect.createOneShot(150L, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Vibrator::class.java)
                vibrator?.vibrate(effect)
            }
        } catch (_: Exception) {
            // Signal failure must never crash the recording session.
        }
    }
}
