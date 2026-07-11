package com.mototracker.data.diagnostics

import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit tests for [FileRideDebugLogger].
 *
 * Exercises the three acceptance criteria without touching Android APIs:
 * 1. ENABLED  → [FileRideDebugLogger.beginRide] creates exactly one file; [FileRideDebugLogger.log]
 *    lines appear with the correct `<ISO-8601-UTC> <tag> <message>` shape.
 * 2. DISABLED → [FileRideDebugLogger.beginRide] creates NO file and [FileRideDebugLogger.log] /
 *    [FileRideDebugLogger.endRide] are true no-ops.
 * 3. I/O failure (unwritable dir) → [FileRideDebugLogger.beginRide] does NOT throw.
 *
 * A [TemporaryFolder] stands in for [Context.getExternalFilesDir].
 * A fixed [Clock] makes timestamp assertions deterministic.
 * An [UnconfinedTestDispatcher] ensures coroutines inside the logger run eagerly within
 * the [runTest] scope so no explicit synchronisation primitives are needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileRideDebugLoggerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    /** Fixed UTC instant used in all timestamp assertions. */
    private val fixedInstant: Instant = Instant.parse("2024-06-01T08:30:00Z")
    private val fixedClock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Fake [AppSettingsSource] that emits a single [AppSettings] snapshot.
     * Does not depend on DataStore or any Android class.
     */
    private class FakeSettingsSource(debugLoggingEnabled: Boolean) : AppSettingsSource {
        override val settings: Flow<AppSettings> =
            flowOf(AppSettings(debugLoggingEnabled = debugLoggingEnabled))
    }

    /** Creates a logger pointing at [logDir] with the given settings and the fixed clock. */
    private fun makeLogger(
        enabled: Boolean,
        logDir: () -> java.io.File?,
        scope: CoroutineScope,
    ): FileRideDebugLogger = FileRideDebugLogger(
        settingsSource = FakeSettingsSource(enabled),
        logDirProvider = logDir,
        scope = scope,
        clock = fixedClock,
    )

    // ── ENABLED path ──────────────────────────────────────────────────────────

    /**
     * When diagnostics are ENABLED, [FileRideDebugLogger.beginRide] must create
     * exactly one file in the ride-logs directory.
     */
    @Test
    fun `ENABLED beginRide creates exactly one log file`() = runTest {
        val dir = tmpFolder.newFolder("ride-logs-1")
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val logger = makeLogger(enabled = true, logDir = { dir }, scope = scope)

        logger.beginRide()
        advanceUntilIdle()

        val files = dir.listFiles() ?: emptyArray()
        assertEquals("exactly one log file must exist after beginRide", 1, files.size)
    }

    /**
     * Log lines must match the format `<ISO-8601-UTC> <tag> <message>`.
     * With a fixed [Clock], the timestamp is deterministic.
     */
    @Test
    fun `ENABLED log lines match ISO-8601-UTC tag message format`() = runTest {
        val dir = tmpFolder.newFolder("ride-logs-2")
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val logger = makeLogger(enabled = true, logDir = { dir }, scope = scope)

        logger.beginRide()
        logger.log("GPS", "lat=50.0 lon=20.0")
        logger.log("LEAN", "angle=12.5")
        logger.endRide()
        advanceUntilIdle()

        val files = dir.listFiles()!!
        assertEquals(1, files.size)
        val lines = files[0].readLines()

        // Each explicit log() call must produce one matching line.
        val gpsLine = lines.first { it.contains("GPS") }
        assertEquals("$fixedInstant GPS lat=50.0 lon=20.0", gpsLine)

        val leanLine = lines.first { it.contains("LEAN") }
        assertEquals("$fixedInstant LEAN angle=12.5", leanLine)
    }

    /**
     * [FileRideDebugLogger.endRide] must close the file so its content is visible to a
     * subsequent reader. A missing endRide would leave the writer open and potentially
     * empty the file on some JVM implementations.
     */
    @Test
    fun `ENABLED endRide flushes content to the file`() = runTest {
        val dir = tmpFolder.newFolder("ride-logs-3")
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val logger = makeLogger(enabled = true, logDir = { dir }, scope = scope)

        logger.beginRide()
        logger.log("SYS", "hello")
        logger.endRide()
        advanceUntilIdle()

        val content = dir.listFiles()!!.first().readText()
        assertTrue("file must contain the logged line", content.contains("SYS hello"))
    }

    // ── DISABLED path ─────────────────────────────────────────────────────────

    /**
     * When diagnostics are DISABLED, [FileRideDebugLogger.beginRide] must NOT create any
     * file — the implementation must be a true no-op with no channel or I/O work.
     */
    @Test
    fun `DISABLED beginRide creates NO file`() = runTest {
        val dir = tmpFolder.newFolder("ride-logs-4")
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val logger = makeLogger(enabled = false, logDir = { dir }, scope = scope)

        logger.beginRide()
        advanceUntilIdle()

        val files = dir.listFiles() ?: emptyArray()
        assertEquals("no file must be created when logging is disabled", 0, files.size)
    }

    /**
     * [FileRideDebugLogger.log] and [FileRideDebugLogger.endRide] must also be no-ops
     * when diagnostics are disabled, even if called in sequence.
     */
    @Test
    fun `DISABLED log and endRide are no-ops`() = runTest {
        val dir = tmpFolder.newFolder("ride-logs-5")
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val logger = makeLogger(enabled = false, logDir = { dir }, scope = scope)

        // Calling all three methods must not crash and must leave the dir empty.
        logger.beginRide()
        logger.log("GPS", "lat=50.0 lon=20.0")
        logger.endRide()
        advanceUntilIdle()

        val files = dir.listFiles() ?: emptyArray()
        assertEquals(0, files.size)
    }

    // ── I/O failure resilience ────────────────────────────────────────────────

    /**
     * When [logDirProvider] returns a path where files cannot be created (e.g. the dir
     * itself is a read-only file), [FileRideDebugLogger.beginRide] must NOT throw.
     * A logging failure must never surface to the ride recording path.
     */
    @Test
    fun `IO failure in beginRide does not throw`() = runTest {
        // Provide a FILE (not a directory) as the "dir" so mkdirs() succeeds but
        // opening a child file inside it fails.
        val notADir = tmpFolder.newFile("not-a-dir.log")
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val logger = makeLogger(enabled = true, logDir = { notADir }, scope = scope)

        var thrown = false
        try {
            logger.beginRide()
            logger.log("GPS", "lat=0 lon=0")
            logger.endRide()
            advanceUntilIdle()
        } catch (_: Exception) {
            thrown = true
        }
        assertFalse("beginRide/log/endRide must never throw even on I/O failure", thrown)
    }

    /**
     * When [logDirProvider] returns `null` (external storage unavailable),
     * [FileRideDebugLogger.beginRide] must be a silent no-op.
     */
    @Test
    fun `null logDirProvider does not throw`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val logger = makeLogger(enabled = true, logDir = { null }, scope = scope)

        var thrown = false
        try {
            logger.beginRide()
            logger.log("GPS", "lat=0 lon=0")
            logger.endRide()
            advanceUntilIdle()
        } catch (_: Exception) {
            thrown = true
        }
        assertFalse("null logDir must not throw", thrown)
    }
}
