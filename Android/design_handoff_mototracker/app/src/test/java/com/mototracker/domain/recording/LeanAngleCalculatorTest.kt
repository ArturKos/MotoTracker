package com.mototracker.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class LeanAngleCalculatorTest {

    private val delta = 0.01

    @Test
    fun `level phone returns 0 degrees`() {
        // Gravity fully along Y (phone face-up, no tilt) → atan2(0, Y) = 0
        val deg = LeanAngleCalculator.leanDegrees(gravityX = 0.0, gravityY = 9.81, gravityZ = 0.0)
        assertEquals(0.0, deg, delta)
    }

    @Test
    fun `gravity fully along X gives 90 degrees unclamped then clamped to 60`() {
        // atan2(9.81, 0) = 90°, clamped → 60
        val deg = LeanAngleCalculator.leanDegrees(gravityX = 9.81, gravityY = 0.0, gravityZ = 0.0)
        assertEquals(60.0, deg, delta)
    }

    @Test
    fun `gravity fully along negative X gives minus 60 degrees`() {
        val deg = LeanAngleCalculator.leanDegrees(gravityX = -9.81, gravityY = 0.0, gravityZ = 0.0)
        assertEquals(-60.0, deg, delta)
    }

    @Test
    fun `45 degree right lean`() {
        // Equal X and YZ magnitude → atan2(x, sqrt(y²+z²)) = 45°
        val g = 9.81
        val component = g / sqrt(2.0)
        val deg = LeanAngleCalculator.leanDegrees(
            gravityX = component,
            gravityY = component,
            gravityZ = 0.0,
        )
        assertEquals(45.0, deg, delta)
    }

    @Test
    fun `45 degree left lean is negative`() {
        val g = 9.81
        val component = g / sqrt(2.0)
        val deg = LeanAngleCalculator.leanDegrees(
            gravityX = -component,
            gravityY = component,
            gravityZ = 0.0,
        )
        assertEquals(-45.0, deg, delta)
    }

    @Test
    fun `result is clamped at plus 60`() {
        // Any large positive X → clamp at +60
        val deg = LeanAngleCalculator.leanDegrees(gravityX = 100.0, gravityY = 0.1, gravityZ = 0.0)
        assertEquals(60.0, deg, delta)
    }

    @Test
    fun `result is clamped at minus 60`() {
        val deg = LeanAngleCalculator.leanDegrees(gravityX = -100.0, gravityY = 0.1, gravityZ = 0.0)
        assertEquals(-60.0, deg, delta)
    }

    @Test
    fun `Z axis contribution reduces computed lean`() {
        // Same X, but larger YZ magnitude → smaller lean angle
        val lean1 = LeanAngleCalculator.leanDegrees(gravityX = 5.0, gravityY = 5.0, gravityZ = 0.0)
        val lean2 = LeanAngleCalculator.leanDegrees(gravityX = 5.0, gravityY = 5.0, gravityZ = 5.0)
        assert(lean2 < lean1) {
            "Adding Z component should reduce the lean angle. lean1=$lean1 lean2=$lean2"
        }
    }
}
