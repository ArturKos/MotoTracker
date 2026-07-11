package com.mototracker.data.diagnostics

import android.content.Context
import com.mototracker.data.settings.AppSettingsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RideDebugLogger] that writes plain-text ISO-8601-UTC ride logs to
 * `<externalFilesDir>/ride-logs/ride-<timestamp>.log`.
 *
 * ### Thread safety
 * All file I/O is serialized through an [Channel.UNLIMITED] [Channel] and processed
 * on a single consumer coroutine running on [Dispatchers.IO].  The 1 Hz recording
 * loop calls [log] from any thread without blocking.
 *
 * ### Failure safety
 * Every file I/O operation is wrapped in `try/catch`.  A disk error can never propagate
 * to the caller or crash a ride.
 *
 * ### Testability
 * The primary constructor accepts a [logDirProvider] lambda, a [CoroutineScope], and a
 * [Clock] so JVM unit tests can substitute a temp directory, an [UnconfinedTestDispatcher],
 * and a fixed clock without touching Android APIs.  The secondary `@Inject` constructor
 * is the production entry point used by Hilt.
 *
 * @param settingsSource  Live settings; the [AppSettingsSource.settings] Flow is observed
 *                        to keep [loggingEnabled] up to date without blocking any caller.
 * @param logDirProvider  Returns the directory for ride logs; may return `null` if external
 *                        storage is not available.
 * @param scope           [CoroutineScope] on which the settings collector and channel
 *                        consumer coroutines are launched.
 * @param clock           Clock used to stamp every log line; default is [Clock.systemUTC].
 */
@Singleton
class FileRideDebugLogger(
    private val settingsSource: AppSettingsSource,
    private val logDirProvider: () -> File?,
    private val scope: CoroutineScope,
    private val clock: Clock,
) : RideDebugLogger {

    /**
     * Production constructor invoked by Hilt.
     *
     * Creates a dedicated [CoroutineScope] backed by [Dispatchers.IO] + [SupervisorJob]
     * so the logger's lifetime matches the process.
     *
     * @param context        Application context (provided by Hilt).
     * @param settingsSource Singleton settings source (provided by Hilt).
     */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        settingsSource: AppSettingsSource,
    ) : this(
        settingsSource = settingsSource,
        logDirProvider = { context.getExternalFilesDir("ride-logs") },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        clock = Clock.systemUTC(),
    )

    // ── Sealed event hierarchy for the IO channel ─────────────────────────────

    private sealed class LogEvent {
        /** Opens a new log file for a ride session. */
        class Open(val file: File) : LogEvent()
        /** Appends a pre-formatted line (no trailing newline needed). */
        class Line(val text: String) : LogEvent()
        /** Flushes and closes the current writer. */
        object Close : LogEvent()
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Cached value of [AppSettingsSource.settings]`.debugLoggingEnabled`. */
    @Volatile
    private var loggingEnabled = false

    /** True between [beginRide] and [endRide] calls; prevents orphan log() calls. */
    @Volatile
    private var isRideActive = false

    /** Unlimited-capacity channel so [log] never suspends the recording loop. */
    private val channel = Channel<LogEvent>(capacity = Channel.UNLIMITED)

    // ── Coroutine consumers ───────────────────────────────────────────────────

    init {
        // Keep loggingEnabled in sync with persisted settings.
        scope.launch {
            settingsSource.settings.collect { settings ->
                loggingEnabled = settings.debugLoggingEnabled
            }
        }

        // Single consumer: serializes all file I/O so writes never interleave.
        scope.launch {
            var writer: BufferedWriter? = null
            for (event in channel) {
                try {
                    when (event) {
                        is LogEvent.Open -> {
                            writer?.close()
                            event.file.parentFile?.mkdirs()
                            writer = event.file.bufferedWriter()
                        }
                        is LogEvent.Line -> {
                            writer?.write(event.text)
                            writer?.newLine()
                        }
                        LogEvent.Close -> {
                            writer?.flush()
                            writer?.close()
                            writer = null
                        }
                    }
                } catch (_: Exception) {
                    // Best-effort: logging must never crash a ride.
                }
            }
        }
    }

    // ── RideDebugLogger ───────────────────────────────────────────────────────

    /**
     * Opens a new ride log file.  No-op when diagnostics are disabled.
     * The file name encodes the UTC start time; colons are replaced with dashes
     * so the name is valid on all filesystems.
     */
    override fun beginRide() {
        if (!loggingEnabled) return
        try {
            val dir = logDirProvider() ?: return
            val ts = Instant.now(clock).toString().replace(":", "-")
            val file = File(dir, "ride-$ts.log")
            isRideActive = true
            channel.trySend(LogEvent.Open(file))
            channel.trySend(LogEvent.Line("${Instant.now(clock)} SYSTEM beginRide"))
        } catch (_: Exception) {}
    }

    /**
     * Enqueues a diagnostic line with an ISO-8601 UTC timestamp prefix.
     * No-op when diagnostics are disabled or no ride is active.
     */
    override fun log(tag: String, message: String) {
        if (!loggingEnabled || !isRideActive) return
        try {
            val line = "${Instant.now(clock)} $tag $message"
            channel.trySend(LogEvent.Line(line))
        } catch (_: Exception) {}
    }

    /**
     * Closes the current ride log file.  No-op when disabled or no ride is active.
     */
    override fun endRide() {
        if (!loggingEnabled || !isRideActive) return
        try {
            isRideActive = false
            channel.trySend(LogEvent.Line("${Instant.now(clock)} SYSTEM endRide"))
            channel.trySend(LogEvent.Close)
        } catch (_: Exception) {}
    }
}
