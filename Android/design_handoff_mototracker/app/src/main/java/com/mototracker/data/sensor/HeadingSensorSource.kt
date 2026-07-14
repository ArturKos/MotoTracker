package com.mototracker.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.mototracker.domain.recording.HeadingCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable seam over the device magnetometer + gravity sensors.
 *
 * The production implementation [SensorManagerHeadingSource] fuses `TYPE_GRAVITY`
 * (or `TYPE_ACCELEROMETER` as a fallback) with `TYPE_MAGNETIC_FIELD` via
 * [HeadingCalculator] to produce a tilt-compensated heading.  Use a fake in unit tests.
 */
interface HeadingSensorSource {
    /**
     * Hot [Flow] of tilt-compensated azimuth values in degrees [0, 360).
     *
     * Begins sensor registration on collection; unregisters on cancel.
     * On-device only (🔬) — use a fake in unit tests.
     */
    val headings: Flow<Float>
}

/**
 * [HeadingSensorSource] backed by [SensorManager].
 *
 * Registers both a gravity/accelerometer sensor and the magnetic-field sensor.
 * On each update it keeps the latest of each vector, then calls
 * [HeadingCalculator.azimuthDegrees] and emits non-null results.
 *
 * Falls back from `TYPE_GRAVITY` to `TYPE_ACCELEROMETER` when the former is absent.
 * The flow completes immediately without emitting if either sensor type is unavailable.
 *
 * @param context Application context supplied by Hilt.
 */
@Singleton
class SensorManagerHeadingSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : HeadingSensorSource {

    override val headings: Flow<Float> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val gravitySensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (gravitySensor == null || magSensor == null) {
            close()
            return@callbackFlow
        }

        var latestGravity: FloatArray? = null
        var latestMagnetic: FloatArray? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GRAVITY,
                    Sensor.TYPE_ACCELEROMETER -> latestGravity = event.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> latestMagnetic = event.values.clone()
                }
                val g = latestGravity ?: return
                val m = latestMagnetic ?: return
                val heading = HeadingCalculator.azimuthDegrees(g, m) ?: return
                trySend(heading)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sm.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
        sm.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sm.unregisterListener(listener) }
    }
}
