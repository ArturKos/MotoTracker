package com.mototracker.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unit tests for [HeadingCalculator.azimuthDegrees].
 *
 * Convention used throughout: gravity = [0, 0, +g] for a face-up flat phone
 * (Android TYPE_GRAVITY reports the reaction to gravitational pull; device +Z points
 * out of the screen, which is "up" when the phone is face-up).
 *
 * Device +Y always points toward the top of the phone.
 * The heading (azimuth) is the angle the device's +Y axis makes with magnetic North,
 * clockwise: 0° = North, 90° = East, 180° = South, 270° = West.
 *
 * The magnetic field vector in the *device frame* depends on which direction the phone
 * faces:
 * - Pointing North (top → North): mag_device = [0, +1, 0]  → 0°
 * - Pointing East  (top → East):  mag_device = [-1, 0, 0]  → 90°
 * - Pointing South (top → South): mag_device = [0, -1, 0]  → 180°
 * - Pointing West  (top → West):  mag_device = [+1, 0, 0]  → 270°
 */
class HeadingCalculatorTest {

    private val g = 9.81f
    private val flatGravity = floatArrayOf(0f, 0f, g)  // face-up, no tilt

    // ── Flat phone — cardinal directions ─────────────────────────────────────

    @Test
    fun `flat phone pointing North returns 0 degrees`() {
        val mag = floatArrayOf(0f, 1f, 0f)
        val result = HeadingCalculator.azimuthDegrees(flatGravity, mag)
        assertNear(0f, result, delta = 1f)
    }

    @Test
    fun `flat phone pointing East returns 90 degrees`() {
        val mag = floatArrayOf(-1f, 0f, 0f)
        val result = HeadingCalculator.azimuthDegrees(flatGravity, mag)
        assertNear(90f, result, delta = 1f)
    }

    @Test
    fun `flat phone pointing South returns 180 degrees`() {
        val mag = floatArrayOf(0f, -1f, 0f)
        val result = HeadingCalculator.azimuthDegrees(flatGravity, mag)
        assertNear(180f, result, delta = 1f)
    }

    @Test
    fun `flat phone pointing West returns 270 degrees`() {
        val mag = floatArrayOf(1f, 0f, 0f)
        val result = HeadingCalculator.azimuthDegrees(flatGravity, mag)
        assertNear(270f, result, delta = 1f)
    }

    // ── Tilt compensation — roll ──────────────────────────────────────────────

    /**
     * Phone rolled 30° to the right (rotated around its Y-axis).
     * Gravity gains an X component; the magnetic field in device frame changes
     * accordingly.  Heading should remain 0° (North).
     */
    @Test
    fun `rolled 30 degrees right still gives 0 degrees heading for North`() {
        val roll = Math.toRadians(30.0)
        val gravity = floatArrayOf(
            (g * sin(roll)).toFloat(),
            0f,
            (g * cos(roll)).toFloat(),
        )
        // Magnetic North in device frame after roll-only rotation: stays [0, 1, 0]
        // because roll is around Y and North = device +Y direction.
        val mag = floatArrayOf(0f, 1f, 0f)
        val result = HeadingCalculator.azimuthDegrees(gravity, mag)
        assertNear(0f, result, delta = 2f)
    }

    /**
     * Phone pitched 30° forward (top tilted toward ground, rotation around device X-axis).
     * The magnetic North vector in the device frame picks up a -Z component.
     * Heading must still be 0° (North) — this validates tilt compensation.
     */
    @Test
    fun `pitched 30 degrees forward still gives 0 degrees heading for North`() {
        val pitch = Math.toRadians(30.0)
        // gravity = [0, g*sin(pitch), g*cos(pitch)] after pitching forward
        val gravity = floatArrayOf(
            0f,
            (g * sin(pitch)).toFloat(),
            (g * cos(pitch)).toFloat(),
        )
        // Magnetic North (world horizontal) in device frame after pitch:
        // world [0,1,0] → device [0, cos(pitch), -sin(pitch)]
        val mag = floatArrayOf(
            0f,
            cos(pitch).toFloat(),
            (-sin(pitch)).toFloat(),
        )
        val result = HeadingCalculator.azimuthDegrees(gravity, mag)
        assertNear(0f, result, delta = 2f)
    }

    /**
     * Combined roll + pitch: 20° roll right and 15° pitch forward while pointing East.
     * Heading must be approximately 90°.
     */
    @Test
    fun `combined roll and pitch while pointing East gives approximately 90 degrees`() {
        val roll  = Math.toRadians(20.0)
        val pitch = Math.toRadians(15.0)
        // Gravity after roll then pitch (approximate: small-angle, independent axes)
        val gravity = floatArrayOf(
            (g * sin(roll) * cos(pitch)).toFloat(),
            (g * sin(pitch)).toFloat(),
            (g * cos(roll) * cos(pitch)).toFloat(),
        )
        // When pointing East: mag North in device = [-cos(pitch), 0, sin(pitch)]
        // (simplified; exact formula omitted for brevity — within 5° is acceptable)
        val mag = floatArrayOf(
            (-cos(pitch)).toFloat(),
            0f,
            sin(pitch).toFloat(),
        )
        val result = HeadingCalculator.azimuthDegrees(gravity, mag)
        assertNear(90f, result, delta = 5f)
    }

    // ── Degenerate inputs — must return null ──────────────────────────────────

    @Test
    fun `gravity zero vector returns null`() {
        val result = HeadingCalculator.azimuthDegrees(
            gravity = floatArrayOf(0f, 0f, 0f),
            geomagnetic = floatArrayOf(0f, 1f, 0f),
        )
        assertNull(result)
    }

    @Test
    fun `geomagnetic zero vector returns null`() {
        val result = HeadingCalculator.azimuthDegrees(
            gravity = flatGravity,
            geomagnetic = floatArrayOf(0f, 0f, 0f),
        )
        assertNull(result)
    }

    @Test
    fun `gravity and geomagnetic collinear returns null`() {
        // Both pointing straight down — cross product is zero.
        val result = HeadingCalculator.azimuthDegrees(
            gravity = floatArrayOf(0f, 0f, 1f),
            geomagnetic = floatArrayOf(0f, 0f, 1f),
        )
        assertNull(result)
    }

    @Test
    fun `gravity and geomagnetic anti-parallel returns null`() {
        val result = HeadingCalculator.azimuthDegrees(
            gravity = floatArrayOf(0f, 0f, 1f),
            geomagnetic = floatArrayOf(0f, 0f, -1f),
        )
        assertNull(result)
    }

    // ── Output range ──────────────────────────────────────────────────────────

    @Test
    fun `result is always in range 0 to 360 exclusive`() {
        // Use a heading that without normalization would be negative (-90°)
        val mag = floatArrayOf(1f, 0f, 0f)  // pointing West → would be -90° unnormalised
        val result = HeadingCalculator.azimuthDegrees(flatGravity, mag)
        requireNonNull(result)
        assert(result!! >= 0f) { "Expected >= 0, got $result" }
        assert(result < 360f)  { "Expected < 360, got $result" }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun assertNear(expected: Float, actual: Float?, delta: Float) {
        requireNonNull(actual)
        assertEquals("Azimuth mismatch", expected, actual!!, delta)
    }

    private fun requireNonNull(value: Float?) {
        if (value == null) throw AssertionError("Expected non-null result but got null")
    }
}
