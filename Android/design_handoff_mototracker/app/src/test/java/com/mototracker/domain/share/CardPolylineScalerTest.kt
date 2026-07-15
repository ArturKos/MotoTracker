package com.mototracker.domain.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CardPolylineScaler] pure layout math.
 */
class CardPolylineScalerTest {

    @Test
    fun `empty input returns empty list`() {
        val result = CardPolylineScaler.scale(
            points = emptyList(),
            targetLeft = 0f, targetTop = 0f, targetWidth = 200f, targetHeight = 100f,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `origin maps to top-left of target rectangle`() {
        val points = listOf(0f to 0f)
        val result = CardPolylineScaler.scale(
            points = points,
            targetLeft = 50f, targetTop = 30f, targetWidth = 200f, targetHeight = 100f,
        )
        assertEquals(1, result.size)
        assertEquals(50f, result[0].first, 0.001f)
        assertEquals(30f, result[0].second, 0.001f)
    }

    @Test
    fun `source bottom-right corner maps to target bottom-right`() {
        val points = listOf(320f to 200f)
        val result = CardPolylineScaler.scale(
            points = points,
            targetLeft = 50f, targetTop = 30f, targetWidth = 200f, targetHeight = 100f,
        )
        assertEquals(250f, result[0].first, 0.001f)
        assertEquals(130f, result[0].second, 0.001f)
    }

    @Test
    fun `mid-point scales proportionally`() {
        val points = listOf(160f to 100f) // exact center of 320x200
        val result = CardPolylineScaler.scale(
            points = points,
            targetLeft = 0f, targetTop = 0f, targetWidth = 400f, targetHeight = 200f,
        )
        assertEquals(200f, result[0].first, 0.001f)
        assertEquals(100f, result[0].second, 0.001f)
    }

    @Test
    fun `multiple points are all scaled`() {
        val points = listOf(0f to 0f, 160f to 100f, 320f to 200f)
        val result = CardPolylineScaler.scale(
            points = points,
            targetLeft = 10f, targetTop = 10f, targetWidth = 320f, targetHeight = 200f,
        )
        assertEquals(3, result.size)
        assertEquals(10f, result[0].first, 0.001f)
        assertEquals(10f, result[0].second, 0.001f)
        assertEquals(330f, result[2].first, 0.001f)
        assertEquals(210f, result[2].second, 0.001f)
    }

    @Test
    fun `non-zero target offset is applied to all points`() {
        val points = listOf(0f to 0f, 320f to 200f)
        val result = CardPolylineScaler.scale(
            points = points,
            targetLeft = 100f, targetTop = 200f, targetWidth = 320f, targetHeight = 200f,
        )
        assertEquals(100f, result[0].first, 0.001f)
        assertEquals(200f, result[0].second, 0.001f)
        assertEquals(420f, result[1].first, 0.001f)
        assertEquals(400f, result[1].second, 0.001f)
    }
}
