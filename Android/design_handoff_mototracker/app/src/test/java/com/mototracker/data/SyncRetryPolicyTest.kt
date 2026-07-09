package com.mototracker.data

import com.mototracker.domain.SyncRetryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JUnit tests for the [SyncRetryPolicy] exponential-backoff helper.
 */
class SyncRetryPolicyTest {

    @Test
    fun `attempt 0 returns base delay of 30 seconds`() {
        assertEquals(30_000L, SyncRetryPolicy.nextRetryDelayMs(0))
    }

    @Test
    fun `each attempt doubles the previous delay`() {
        val delays = (0..5).map { SyncRetryPolicy.nextRetryDelayMs(it) }
        for (i in 1..5) {
            assertEquals("attempt $i should double attempt ${i - 1}", delays[i - 1] * 2, delays[i])
        }
    }

    @Test
    fun `delay is capped at 1 hour`() {
        val capped = SyncRetryPolicy.nextRetryDelayMs(100)
        assertEquals(3_600_000L, capped)
    }

    @Test
    fun `negative attempt count is treated as 0`() {
        assertEquals(SyncRetryPolicy.nextRetryDelayMs(0), SyncRetryPolicy.nextRetryDelayMs(-5))
    }

    @Test
    fun `cap kicks in at or before attempt 7`() {
        val atSeven = SyncRetryPolicy.nextRetryDelayMs(7)
        assertEquals(3_600_000L, atSeven)
    }

    @Test
    fun `all returned values are positive`() {
        for (attempt in 0..20) {
            assertTrue(SyncRetryPolicy.nextRetryDelayMs(attempt) > 0)
        }
    }
}
