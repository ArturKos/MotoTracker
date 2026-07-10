package com.mototracker.data.repository

import app.cash.turbine.test
import com.mototracker.data.model.FeedType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Thin tests verifying the static seed data exposed by [FeedRepositoryImpl]. */
class FeedRepositoryImplTest {

    private val repo = FeedRepositoryImpl()

    @Test
    fun `observeFeed emits exactly three seed events`() = runTest {
        repo.observeFeed().test {
            assertEquals(3, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `first seed event is Marek START at 09h12`() = runTest {
        repo.observeFeed().test {
            val first = awaitItem().first()
            assertEquals("Marek", first.who)
            assertEquals(FeedType.START, first.type)
            assertEquals("09:12", first.timeLabel)
            assertNull(first.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `second seed event is Ola MAX with speed value`() = runTest {
        repo.observeFeed().test {
            val second = awaitItem()[1]
            assertEquals("Ola", second.who)
            assertEquals(FeedType.MAX, second.type)
            assertTrue("value should contain speed", second.value?.contains("148") == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `third seed event is Piotr FINISH at 12h40`() = runTest {
        repo.observeFeed().test {
            val third = awaitItem()[2]
            assertEquals("Piotr", third.who)
            assertEquals(FeedType.FINISH, third.type)
            assertEquals("12:40", third.timeLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
