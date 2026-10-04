package com.mototracker.data.recording

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.mototracker.domain.recording.RecordingEngineState
import com.mototracker.domain.recording.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Unit tests for [JournalRecordingSessionStore]. */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalRecordingSessionStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(dir: File = File(tmp.root, "ride")) =
        JournalRecordingSessionStore(dir, legacy = null, io = Dispatchers.Unconfined)

    private fun point(i: Int) = TrackPoint(lat = 53.0 + i * 1e-4, lng = 14.5, ele = 10.0 + i, t = 1_000L * i)

    private fun snapshot(n: Int, startMs: Long = 42L, paused: Boolean = false) = ActiveSessionSnapshot(
        engineState = RecordingEngineState(
            prevLat = 53.0, prevLng = 14.5, prevAlt = 10.0, prevTimeMs = 1_000L * n,
            distanceKm = n * 0.01, durationSec = n.toLong(), movingSec = n.toLong(),
            currentSpeedKmh = 36.0, maxSpeedKmh = 80.0,
            currentLeanDeg = 5.0, maxLeanDeg = 30.0, maxLeanLeftDeg = 25.0, maxLeanRightDeg = 30.0,
            altitudeM = 10.0, elevGainM = 3.0, headingDeg = 90f,
            pathPoints = List(n) { point(it) },
            speedOverTime = List(n) { it.toLong() to 30.0 + it },
            elevOverDist = List(n) { it * 0.01 to 10.0 + it },
            tankCapacityL = 15.0, anchorKm = 0.0, anchorLitres = 15.0,
            leanBucketCounts = listOf(1, 2, 3, 4, 5),
        ),
        recordingStartMs = startMs,
        bikeId = "bike-1",
        paused = paused,
        pendingRefuels = listOf(PendingRefuel(epochMs = 7L, litres = 10.0, pricePerL = 6.5)),
    )

    @Test
    fun `round trips a snapshot including lean extremes and histogram`() = runTest {
        val s = store()
        val original = snapshot(5)
        s.save(original)
        assertEquals(original, store(File(tmp.root, "ride")).snapshot.first())
    }

    @Test
    fun `subsequent saves append only the new points`() = runTest {
        val dir = File(tmp.root, "ride")
        val s = store(dir)
        s.save(snapshot(3))
        val pathFile = File(dir, "path.log")
        val firstBytes = pathFile.readText()
        s.save(snapshot(5))
        val after = pathFile.readText()
        assertTrue("earlier lines must stay untouched", after.startsWith(firstBytes))
        assertEquals(5, after.lines().count { it.isNotEmpty() })
        assertEquals(snapshot(5), store(dir).snapshot.first())
    }

    @Test
    fun `a new session with fewer points rewrites the journal`() = runTest {
        val dir = File(tmp.root, "ride")
        val s = store(dir)
        s.save(snapshot(6, startMs = 1L))
        s.save(snapshot(2, startMs = 2L))
        assertEquals(2, File(dir, "path.log").readText().lines().count { it.isNotEmpty() })
        assertEquals(snapshot(2, startMs = 2L), store(dir).snapshot.first())
    }

    @Test
    fun `uncommitted torn tail is ignored on load`() = runTest {
        val dir = File(tmp.root, "ride")
        store(dir).save(snapshot(3))
        // Crash after appending to the journal but before meta.json was replaced.
        File(dir, "path.log").appendText("53.9,14.9,1.0,9\n53.8,14.")
        val loaded = store(dir).snapshot.first()
        assertEquals(snapshot(3), loaded)
    }

    @Test
    fun `clear removes everything`() = runTest {
        val dir = File(tmp.root, "ride")
        val s = store(dir)
        s.save(snapshot(3))
        s.clear()
        assertNull(s.snapshot.first())
        assertFalse(File(dir, "meta.json").exists())
    }

    @Test
    fun `empty store yields null`() = runTest {
        assertNull(store().snapshot.first())
    }

    @Test
    fun `old DataStore snapshot is migrated into the journal and removed`() = runTest {
        val dsScope = TestScope(UnconfinedTestDispatcher())
        val ds = PreferenceDataStoreFactory.create(
            scope = dsScope,
            produceFile = { tmp.newFile("legacy.preferences_pb") },
        )
        val legacySnapshot = snapshot(4)
        ds.edit { it[JournalRecordingSessionStore.LEGACY_KEY] = encode(legacySnapshot) }
        val dir = File(tmp.root, "ride")
        val s = JournalRecordingSessionStore(dir, legacy = ds, io = Dispatchers.Unconfined)

        assertEquals(legacySnapshot, s.snapshot.first())
        assertNull(ds.data.first()[JournalRecordingSessionStore.LEGACY_KEY])
        assertEquals(legacySnapshot, store(dir).snapshot.first())
        dsScope.cancel()
    }
}
