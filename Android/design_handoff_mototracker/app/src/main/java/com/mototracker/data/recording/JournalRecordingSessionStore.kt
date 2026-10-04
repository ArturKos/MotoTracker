package com.mototracker.data.recording

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mototracker.domain.recording.TrackPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crash-recovery store (B20) that writes the active ride as an append-only journal.
 *
 * The previous store re-serialised the whole ride — every track point, speed and elevation
 * sample — into the shared settings DataStore on every GPS fix, so the cost of each write grew
 * with the length of the ride. Here the three growing lists live in their own files and only the
 * entries added since the last save are appended; the small scalar part (distance, fuel anchors,
 * lean extremes, …) is rewritten atomically. Each save is therefore O(new points), not O(ride).
 *
 * Layout under [dir]:
 * - `path.log`  — one `lat,lng,ele,t` line per track point (`t` empty when unknown)
 * - `spd.log`   — one `t,v` line per speed sample
 * - `elev.log`  — one `d,a` line per elevation sample
 * - `meta.json` — [encode]d snapshot with empty lists plus the committed list sizes
 *
 * Crash safety: lists are appended before `meta.json` is replaced (write to a temp file, then
 * rename), and loading reads only as many lines as `meta.json` commits, so a torn append or a
 * crash between the two steps never yields a half-written snapshot.
 *
 * A snapshot left by the old DataStore-based store is migrated on first [snapshot] read.
 *
 * @param dir        Directory holding the journal files (created on demand).
 * @param legacy     DataStore that may still hold an old-format snapshot; null in unit tests.
 * @param io         Dispatcher for file I/O.
 */
@Singleton
class JournalRecordingSessionStore internal constructor(
    private val dir: File,
    private val legacy: DataStore<Preferences>?,
    private val io: CoroutineDispatcher,
) : RecordingSessionStore {

    /** Production constructor: journal under the app's private files directory. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>,
    ) : this(File(context.filesDir, DIR_NAME), dataStore, Dispatchers.IO)

    private val mutex = Mutex()

    /** What is already on disk for the current session; null = unknown, rewrite on next save. */
    private var written: Written? = null

    private data class Written(val startMs: Long, val path: Int, val spd: Int, val elev: Int)

    private val pathFile get() = File(dir, "path.log")
    private val spdFile get() = File(dir, "spd.log")
    private val elevFile get() = File(dir, "elev.log")
    private val metaFile get() = File(dir, "meta.json")

    /** Emits the stored snapshot (or null) read from disk at collection time. */
    override val snapshot: Flow<ActiveSessionSnapshot?> = flow {
        emit(mutex.withLock { withContext(io) { loadOrMigrate() } })
    }

    override suspend fun save(s: ActiveSessionSnapshot) {
        mutex.withLock { withContext(io) { write(s) } }
    }

    override suspend fun clear() {
        mutex.withLock {
            withContext(io) {
                listOf(pathFile, spdFile, elevFile, metaFile).forEach { it.delete() }
                written = null
            }
            legacy?.edit { it.remove(LEGACY_KEY) }
        }
    }

    // ── Write path ───────────────────────────────────────────────────────────

    private fun write(s: ActiveSessionSnapshot) {
        dir.mkdirs()
        val e = s.engineState
        val w = written
        val canAppend = w != null &&
            w.startMs == s.recordingStartMs &&
            e.pathPoints.size >= w.path &&
            e.speedOverTime.size >= w.spd &&
            e.elevOverDist.size >= w.elev
        if (canAppend) {
            appendLines(pathFile, e.pathPoints.subList(w!!.path, e.pathPoints.size).map(::pathLine))
            appendLines(spdFile, e.speedOverTime.subList(w.spd, e.speedOverTime.size).map { "${it.first},${it.second}" })
            appendLines(elevFile, e.elevOverDist.subList(w.elev, e.elevOverDist.size).map { "${it.first},${it.second}" })
        } else {
            rewrite(pathFile, e.pathPoints.map(::pathLine))
            rewrite(spdFile, e.speedOverTime.map { "${it.first},${it.second}" })
            rewrite(elevFile, e.elevOverDist.map { "${it.first},${it.second}" })
        }
        writeMeta(s)
        written = Written(s.recordingStartMs, e.pathPoints.size, e.speedOverTime.size, e.elevOverDist.size)
    }

    private fun writeMeta(s: ActiveSessionSnapshot) {
        val e = s.engineState
        val scalarsOnly = s.copy(
            engineState = e.copy(pathPoints = emptyList(), speedOverTime = emptyList(), elevOverDist = emptyList()),
        )
        val json = JSONObject(encode(scalarsOnly))
            .put("np", e.pathPoints.size)
            .put("ns", e.speedOverTime.size)
            .put("ne", e.elevOverDist.size)
            .toString()
        val tmp = File(dir, "meta.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(metaFile)) {
            metaFile.delete()
            tmp.renameTo(metaFile)
        }
    }

    private fun appendLines(file: File, lines: List<String>) {
        if (lines.isEmpty()) return
        file.appendText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun rewrite(file: File, lines: List<String>) {
        file.writeText(if (lines.isEmpty()) "" else lines.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun pathLine(p: TrackPoint) = "${p.lat},${p.lng},${p.ele},${p.t ?: ""}"

    // ── Read path ────────────────────────────────────────────────────────────

    private suspend fun loadOrMigrate(): ActiveSessionSnapshot? {
        load()?.let { return it }
        val ds = legacy ?: return null
        val old = ds.data.first()[LEGACY_KEY] ?: return null
        val migrated = decode(old)
        if (migrated != null) write(migrated) else written = null
        ds.edit { it.remove(LEGACY_KEY) }
        return migrated
    }

    private fun load(): ActiveSessionSnapshot? {
        if (!metaFile.exists()) return null
        return try {
            val meta = JSONObject(metaFile.readText())
            val base = decode(meta.toString()) ?: return null
            val path = readLines(pathFile, meta.optInt("np", 0)) { f ->
                TrackPoint(
                    lat = f[0].toDouble(),
                    lng = f[1].toDouble(),
                    ele = f[2].toDouble(),
                    t = f.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toLong(),
                )
            }
            val spd = readLines(spdFile, meta.optInt("ns", 0)) { f -> f[0].toLong() to f[1].toDouble() }
            val elev = readLines(elevFile, meta.optInt("ne", 0)) { f -> f[0].toDouble() to f[1].toDouble() }
            val snapshot = base.copy(
                engineState = base.engineState.copy(pathPoints = path, speedOverTime = spd, elevOverDist = elev),
            )
            // Lines past the committed counts (torn append) are dropped on the next save.
            written = null
            snapshot
        } catch (_: Exception) {
            null
        }
    }

    /** Parses up to [limit] well-formed lines; stops at the first malformed (torn) line. */
    private fun <T> readLines(file: File, limit: Int, parse: (List<String>) -> T): List<T> {
        if (!file.exists() || limit <= 0) return emptyList()
        val out = ArrayList<T>(limit)
        file.useLines { lines ->
            for (line in lines) {
                if (out.size >= limit) break
                val item = runCatching { parse(line.split(',')) }.getOrNull() ?: break
                out += item
            }
        }
        return out
    }

    companion object {
        /** Directory name under `filesDir`. */
        const val DIR_NAME = "active_ride"

        /** DataStore key used by the old whole-snapshot store; migrated then removed. */
        val LEGACY_KEY = stringPreferencesKey("active_recording_session")
    }
}
