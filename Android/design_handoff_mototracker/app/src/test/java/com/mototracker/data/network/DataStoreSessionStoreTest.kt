package com.mototracker.data.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [DataStoreSessionStore] verifying that all three session fields
 * (cookie, email, writeApiKey) are correctly persisted, cleared, and emitted via
 * the [DataStoreSessionStore.session] flow.
 *
 * A file-backed [PreferenceDataStoreFactory] with a [TestScope] is used so the
 * DataStore coroutine machinery integrates cleanly with the test dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSessionStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())

    private lateinit var store: DataStoreSessionStore

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_session.preferences_pb") },
        )
        store = DataStoreSessionStore(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    /**
     * Saving all three fields must result in the session flow emitting a [SessionState]
     * with matching values and [SessionState.isAuthenticated] == true.
     */
    @Test
    fun `save persists cookie email and writeApiKey`() = runTest(testDispatcher) {
        store.save("PHPSESSID=abc", "rider@example.com", "wak_key123")

        store.session.test {
            val state = awaitItem()
            assertEquals("PHPSESSID=abc", state.cookie)
            assertEquals("rider@example.com", state.email)
            assertEquals("wak_key123", state.writeApiKey)
            assertTrue(state.isAuthenticated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Saving with a null cookie must remove the cookie key; other fields are unaffected.
     */
    @Test
    fun `save with null cookie removes cookie from prefs`() = runTest(testDispatcher) {
        store.save("PHPSESSID=abc", "rider@example.com", "wak_key123")
        store.save(null, "rider@example.com", "wak_key123")

        store.session.test {
            val state = awaitItem()
            assertNull("cookie must be null", state.cookie)
            assertEquals("wak_key123", state.writeApiKey)
            assertTrue("still authenticated via writeApiKey", state.isAuthenticated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Saving with a null writeApiKey must remove the writeApiKey key; other fields unaffected.
     */
    @Test
    fun `save with null writeApiKey removes writeApiKey from prefs`() = runTest(testDispatcher) {
        store.save("PHPSESSID=abc", "rider@example.com", "wak_key123")
        store.save("PHPSESSID=abc", "rider@example.com", null)

        store.session.test {
            val state = awaitItem()
            assertEquals("PHPSESSID=abc", state.cookie)
            assertNull("writeApiKey must be null", state.writeApiKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * clear() must remove all three keys atomically; the session flow emits
     * [SessionState.UNAUTHENTICATED] and [SessionState.isAuthenticated] is false.
     */
    @Test
    fun `clear removes all session fields and emits UNAUTHENTICATED`() = runTest(testDispatcher) {
        store.save("PHPSESSID=abc", "rider@example.com", "wak_key123")
        store.clear()

        store.session.test {
            val state = awaitItem()
            assertNull("cookie must be null after clear", state.cookie)
            assertNull("writeApiKey must be null after clear", state.writeApiKey)
            assertFalse("isAuthenticated must be false after clear", state.isAuthenticated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The session flow must reflect a null writeApiKey when no writeApiKey was ever saved.
     */
    @Test
    fun `session flow emits null writeApiKey when key was never saved`() = runTest(testDispatcher) {
        store.save("PHPSESSID=abc", "rider@example.com", null)

        store.session.test {
            val state = awaitItem()
            assertNull("writeApiKey must be null", state.writeApiKey)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
