package com.mototracker.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.mototracker.domain.recording.LeanAngleCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable seam over the device gravity sensor.
 *
 * The production implementation [SensorManagerLeanSource] reads TYPE_GRAVITY events and
 * converts them to lean angles via [LeanAngleCalculator]. Use a fake in unit tests.
 */
interface LeanSensorSource {
    /**
     * Hot [Flow] of lean angles in degrees.
     *
     * Begins sensor registration on collection; unregisters on cancel.
     * On-device only (🔬) — use a fake in unit tests.
     */
    val leanAngles: Flow<Double>
}

/**
 * [LeanSensorSource] backed by [SensorManager] and the TYPE_GRAVITY sensor.
 *
 * If the device has no gravity sensor (rare), the flow completes immediately without emitting.
 *
 * @param context Application context supplied by Hilt.
 */
@Singleton
class SensorManagerLeanSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LeanSensorSource {

    override val leanAngles: Flow<Double> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_GRAVITY) {
                    val deg = LeanAngleCalculator.leanDegrees(
                        event.values[0].toDouble(),
                        event.values[1].toDouble(),
                        event.values[2].toDouble(),
                    )
                    trySend(deg)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sm.unregisterListener(listener) }
    }
}
