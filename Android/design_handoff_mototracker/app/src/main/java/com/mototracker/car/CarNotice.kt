package com.mototracker.car

import androidx.annotation.StringRes
import com.mototracker.R

/**
 * One-shot messages the ride shows on the Android Auto screen as a toast.
 *
 * @property messageRes Localised text displayed to the rider.
 */
enum class CarNotice(@StringRes val messageRes: Int) {
    /** Android refused to run the recording service (phone locked); the ride was paused. */
    RECORDING_BLOCKED(R.string.car_recording_blocked),

    /** Saving the finished ride failed; it stays paused so Finish can be retried. */
    SAVE_FAILED(R.string.toast_ride_save_failed),
}
