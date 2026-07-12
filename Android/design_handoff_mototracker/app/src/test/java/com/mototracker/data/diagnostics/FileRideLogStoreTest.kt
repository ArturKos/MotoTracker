package com.mototracker.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [FileRideLogStore].
 *
 * All tests use a [TemporaryFolder] in place of [android.content.Context.getExternalFilesDir]
 * so no Android runtime is required.
 *
 * Acceptance criteria:
 * 1. [FileRideLogStore.totalBytes] sums the sizes of all files.
 * 2. [FileRideLogStore.latestLog] picks the newest file by last-modified time.
 * 3. [FileRideLogStore.clear] deletes every file and returns the count.
 * 4. Empty directory and null directory provider are handled gracefully (no-op / returns 0 / null).
 */
class FileRideLogStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun storeFor(dir: java.io.File?) = FileRideLogStore(logDirProvider = { dir })

    // ── totalBytes ────────────────────────────────────────────────────────────

    @Test
    fun `totalBytes sums sizes of all files`() {
        val dir = tmpFolder.newFolder("logs-total")
        dir.resolve("a.log").writeText("hello")    // 5 bytes
        dir.resolve("b.log").writeText("world!!")  // 7 bytes
        assertEquals(12L, storeFor(dir).totalBytes())
    }

    @Test
    fun `totalBytes returns 0 for empty directory`() {
        val dir = tmpFolder.newFolder("logs-empty")
        assertEquals(0L, storeFor(dir).totalBytes())
    }

    @Test
    fun `totalBytes returns 0 when logDirProvider returns null`() {
        assertEquals(0L, storeFor(null).totalBytes())
    }

    @Test
    fun `totalBytes returns 0 when directory does not exist`() {
        val nonExistent = tmpFolder.root.resolve("does-not-exist")
        assertEquals(0L, storeFor(nonExistent).totalBytes())
    }

    // ── latestLog ─────────────────────────────────────────────────────────────

    @Test
    fun `latestLog returns newest file by lastModified`() {
        val dir = tmpFolder.newFolder("logs-latest")
        val old = dir.resolve("old.log").also { it.writeText("old") }
        val new = dir.resolve("new.log").also { it.writeText("new") }
        // Force a time difference so lastModified is distinct.
        old.setLastModified(1_000L)
        new.setLastModified(2_000L)
        assertEquals(new, storeFor(dir).latestLog())
    }

    @Test
    fun `latestLog returns null for empty directory`() {
        val dir = tmpFolder.newFolder("logs-latest-empty")
        assertNull(storeFor(dir).latestLog())
    }

    @Test
    fun `latestLog returns null when logDirProvider returns null`() {
        assertNull(storeFor(null).latestLog())
    }

    @Test
    fun `latestLog returns null when directory does not exist`() {
        val nonExistent = tmpFolder.root.resolve("no-dir")
        assertNull(storeFor(nonExistent).latestLog())
    }

    @Test
    fun `latestLog returns single file when only one exists`() {
        val dir = tmpFolder.newFolder("logs-single")
        val f = dir.resolve("ride.log").also { it.writeText("data") }
        assertEquals(f, storeFor(dir).latestLog())
    }

    // ── clear ─────────────────────────────────────────────────────────────────

    @Test
    fun `clear deletes all files and returns count`() {
        val dir = tmpFolder.newFolder("logs-clear")
        dir.resolve("a.log").writeText("a")
        dir.resolve("b.log").writeText("b")
        dir.resolve("c.log").writeText("c")
        val count = storeFor(dir).clear()
        assertEquals(3, count)
        assertNotNull(dir.listFiles())
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun `clear returns 0 for empty directory`() {
        val dir = tmpFolder.newFolder("logs-clear-empty")
        assertEquals(0, storeFor(dir).clear())
    }

    @Test
    fun `clear returns 0 when logDirProvider returns null`() {
        assertEquals(0, storeFor(null).clear())
    }

    @Test
    fun `clear returns 0 when directory does not exist`() {
        val nonExistent = tmpFolder.root.resolve("no-dir-clear")
        assertEquals(0, storeFor(nonExistent).clear())
    }

    // ── Failure safety ────────────────────────────────────────────────────────

    @Test
    fun `all methods are no-ops when logDirProvider throws`() {
        val throwingStore = FileRideLogStore(logDirProvider = { throw RuntimeException("storage error") })
        // None of these must throw.
        assertEquals(0L, throwingStore.totalBytes())
        assertNull(throwingStore.latestLog())
        assertEquals(0, throwingStore.clear())
    }
}
