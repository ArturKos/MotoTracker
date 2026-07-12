package com.mototracker.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [DataStoreAuthStateStore].
 *
 * Uses [PreferenceDataStoreFactory] backed by a [TemporaryFolder] so tests run on the JVM
 * without an Android device. Each test gives the DataStore its own [TestScope] which is
 * cancelled before the [runTest] block exits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreAuthStateStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createStore(fileName: String): Pair<DataStoreAuthStateStore, TestScope> {
        val dsScope = TestScope(UnconfinedTestDispatcher())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dsScope,
            produceFile = { tmpFolder.newFile(fileName) },
        )
        return DataStoreAuthStateStore(dataStore) to dsScope
    }

    @Test
    fun `default value is NONE when no key has been written`() = runTest {
        val (store, dsScope) = createStore("default.preferences_pb")
        store.authState.test {
            assertEquals(AuthState.NONE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    @Test
    fun `set GUEST persists and reads back GUEST`() = runTest {
        val (store, dsScope) = createStore("guest.preferences_pb")
        store.authState.test {
            assertEquals(AuthState.NONE, awaitItem())
            store.set(AuthState.GUEST)
            assertEquals(AuthState.GUEST, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    @Test
    fun `set AUTHED persists and reads back AUTHED`() = runTest {
        val (store, dsScope) = createStore("authed.preferences_pb")
        store.authState.test {
            assertEquals(AuthState.NONE, awaitItem())
            store.set(AuthState.AUTHED)
            assertEquals(AuthState.AUTHED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    @Test
    fun `round-trip all AuthState values in sequence`() = runTest {
        val (store, dsScope) = createStore("roundtrip.preferences_pb")
        store.authState.test {
            assertEquals(AuthState.NONE, awaitItem())
            for (state in AuthState.entries) {
                store.set(state)
                assertEquals(state, awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    @Test
    fun `set AUTHED then set NONE reverts to NONE`() = runTest {
        val (store, dsScope) = createStore("revert.preferences_pb")
        store.authState.test {
            assertEquals(AuthState.NONE, awaitItem())
            store.set(AuthState.AUTHED)
            assertEquals(AuthState.AUTHED, awaitItem())
            store.set(AuthState.NONE)
            assertEquals(AuthState.NONE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    @Test
    fun `unknown stored string falls back to NONE`() = runTest {
        val dsScope = TestScope(UnconfinedTestDispatcher())
        val rawDataStore = PreferenceDataStoreFactory.create(
            scope = dsScope,
            produceFile = { tmpFolder.newFile("unknown.preferences_pb") },
        )
        // Write an unrecognized value directly — simulates a corrupt or legacy stored entry.
        rawDataStore.edit { prefs ->
            prefs[stringPreferencesKey("auth_state")] = "UNKNOWN_FUTURE_VALUE"
        }
        val store = DataStoreAuthStateStore(rawDataStore)
        store.authState.test {
            assertEquals(AuthState.NONE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }
}
