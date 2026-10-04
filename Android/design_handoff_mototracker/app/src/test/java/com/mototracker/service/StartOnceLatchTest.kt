package com.mototracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Unit tests for [StartOnceLatch]. */
class StartOnceLatchTest {

    @Test
    fun `first acquire succeeds and later ones fail`() {
        val latch = StartOnceLatch()
        assertTrue(latch.tryAcquire())
        assertFalse(latch.tryAcquire())
        assertFalse(latch.tryAcquire())
    }

    @Test
    fun `concurrent acquires succeed exactly once`() {
        val latch = StartOnceLatch()
        val wins = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(8)
        val gate = CountDownLatch(1)
        repeat(64) {
            pool.execute {
                gate.await()
                if (latch.tryAcquire()) wins.incrementAndGet()
            }
        }
        gate.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, wins.get())
    }
}
