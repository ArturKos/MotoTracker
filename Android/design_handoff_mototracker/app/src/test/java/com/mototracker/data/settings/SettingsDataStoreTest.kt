package com.mototracker.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [SettingsDataStore]'s B7 setters.
 *
 * Uses [PreferenceDataStoreFactory] backed by a [TemporaryFolder] so the tests
 * run on the JVM without an Android device. Each test gives the DataStore its
 * own [TestScope] that is explicitly cancelled before the [runTest] block exits,
 * preventing [kotlinx.coroutines.test.UncompletedCoroutinesError].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    /**
     * Creates a [SettingsDataStore] backed by a fresh file [fileName] and a
     * dedicated [TestScope]. The caller MUST call [TestScope.cancel] on the
     * returned scope after the test body to prevent [UncompletedCoroutinesError].
     */
    private fun createStore(fileName: String): Pair<SettingsDataStore, TestScope> {
        val dsScope = TestScope(UnconfinedTestDispatcher())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dsScope,
            produceFile = { tmpFolder.newFile(fileName) },
        )
        return SettingsDataStore(dataStore) to dsScope
    }

    // ── Boolean new setters ───────────────────────────────────────────────────

    /** [SettingsDataStore.setAutoPause] persists false then true via [settings]. */
    @Test
    fun `setAutoPause persists value and is read back`() = runTest {
        val (store, dsScope) = createStore("autoPause.preferences_pb")
        store.settings.test {
            assertTrue("default autoPause should be true", awaitItem().autoPause)

            store.setAutoPause(false)
            assertFalse("autoPause should now be false", awaitItem().autoPause)

            store.setAutoPause(true)
            assertTrue("autoPause should be restored to true", awaitItem().autoPause)
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    /** [SettingsDataStore.setKeepScreenOn] persists true then false via [settings]. */
    @Test
    fun `setKeepScreenOn persists value and is read back`() = runTest {
        val (store, dsScope) = createStore("keepScreenOn.preferences_pb")
        store.settings.test {
            assertFalse("default keepScreenOn should be false", awaitItem().keepScreenOn)

            store.setKeepScreenOn(true)
            assertTrue("keepScreenOn should now be true", awaitItem().keepScreenOn)

            store.setKeepScreenOn(false)
            assertFalse("keepScreenOn should be restored to false", awaitItem().keepScreenOn)
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    /** [SettingsDataStore.setAndroidAutoEnabled] persists true then false via [settings]. */
    @Test
    fun `setAndroidAutoEnabled persists value and is read back`() = runTest {
        val (store, dsScope) = createStore("androidAuto.preferences_pb")
        store.settings.test {
            assertFalse("default androidAutoEnabled should be false", awaitItem().androidAutoEnabled)

            store.setAndroidAutoEnabled(true)
            assertTrue("androidAutoEnabled should now be true", awaitItem().androidAutoEnabled)

            store.setAndroidAutoEnabled(false)
            assertFalse("androidAutoEnabled should be restored to false", awaitItem().androidAutoEnabled)
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    // ── Broadcast profile String setters ─────────────────────────────────────

    /** [SettingsDataStore.setBcName] defaults to empty then persists via [settings]. */
    @Test
    fun `setBcName persists value and is read back`() = runTest {
        val (store, dsScope) = createStore("bcName.preferences_pb")
        store.settings.test {
            assertEquals("", awaitItem().bcName)

            store.setBcName("Artur")
            assertEquals("Artur", awaitItem().bcName)

            store.setBcName("Rider42")
            assertEquals("Rider42", awaitItem().bcName)
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    /** [SettingsDataStore.setBcPhone] defaults to empty then persists via [settings]. */
    @Test
    fun `setBcPhone persists value and is read back`() = runTest {
        val (store, dsScope) = createStore("bcPhone.preferences_pb")
        store.settings.test {
            assertEquals("", awaitItem().bcPhone)

            store.setBcPhone("+48 123 456 789")
            assertEquals("+48 123 456 789", awaitItem().bcPhone)
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    /** [SettingsDataStore.setBcOrigin] defaults to empty then persists via [settings]. */
    @Test
    fun `setBcOrigin persists value and is read back`() = runTest {
        val (store, dsScope) = createStore("bcOrigin.preferences_pb")
        store.settings.test {
            assertEquals("", awaitItem().bcOrigin)

            store.setBcOrigin("Warszawa")
            assertEquals("Warszawa", awaitItem().bcOrigin)
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    /** [SettingsDataStore.setBcSocial] defaults to empty then persists via [settings]. */
    @Test
    fun `setBcSocial persists value and is read back`() = runTest {
        val (store, dsScope) = createStore("bcSocial.preferences_pb")
        store.settings.test {
            assertEquals("", awaitItem().bcSocial)

            store.setBcSocial("@artur_moto")
            assertEquals("@artur_moto", awaitItem().bcSocial)
            cancelAndIgnoreRemainingEvents()
        }
        dsScope.cancel()
    }

    // ── Default alignment check ───────────────────────────────────────────────

    /**
     * [AppSettings.accent] default must be "#00D1B2" — the same hex value
     * used by the TEAL swatch in [AccentColor.hex], so a fresh install shows
     * the swatch selected.
     */
    @Test
    fun `default accent colour aligns with TEAL swatch hex`() {
        assertEquals("#00D1B2", AppSettings().accent)
    }
}
