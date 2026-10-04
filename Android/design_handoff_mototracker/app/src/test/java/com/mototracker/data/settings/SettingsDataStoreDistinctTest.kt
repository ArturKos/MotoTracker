package com.mototracker.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Verifies that [SettingsDataStore.settings] does not re-emit when the shared DataStore is
 * written under keys that are not settings (e.g. the active-recording snapshot written on every
 * GPS fix). Without this, every settings subscriber woke up once per second during a ride.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataStoreDistinctTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `unrelated key writes do not re-emit settings`() = runTest {
        val dsScope = TestScope(UnconfinedTestDispatcher())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dsScope,
            produceFile = { tmpFolder.newFile("distinct.preferences_pb") },
        )
        val store = SettingsDataStore(dataStore)
        val unrelated = stringPreferencesKey("active_recording_session")

        store.settings.test {
            awaitItem()
            dataStore.edit { it[unrelated] = "snapshot-1" }
            dataStore.edit { it[unrelated] = "snapshot-2" }
            expectNoEvents()

            store.setKeepScreenOn(true)
            assertTrue("a real settings change must still emit", awaitItem().keepScreenOn)
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }
}
