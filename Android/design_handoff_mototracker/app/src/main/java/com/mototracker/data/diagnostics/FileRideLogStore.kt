package com.mototracker.data.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RideLogStore] backed by the app-specific external files directory.
 *
 * ### Testability
 * The primary constructor accepts a [logDirProvider] lambda so JVM unit tests can
 * substitute a temp directory without touching Android APIs.  The secondary `@Inject`
 * constructor is the production entry point used by Hilt.
 *
 * ### Failure safety
 * Every file I/O call is wrapped in `try/catch`; no method ever throws, and null /
 * unavailable directories are handled gracefully.
 *
 * @param logDirProvider Returns the ride-logs directory; may return null if external
 *                       storage is unavailable.
 */
@Singleton
class FileRideLogStore(
    private val logDirProvider: () -> File?,
) : RideLogStore {

    /**
     * Production constructor invoked by Hilt.
     *
     * @param context Application context (provided by Hilt).
     */
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        logDirProvider = { context.getExternalFilesDir("ride-logs") },
    )

    /**
     * Returns the newest file in the ride-logs directory by last-modified time.
     * Returns null when no files exist, the directory is unavailable, or on any I/O
     * error.
     */
    override fun latestLog(): File? = try {
        logDirProvider()
            ?.takeIf { it.exists() }
            ?.listFiles()
            ?.maxByOrNull { it.lastModified() }
    } catch (_: Exception) {
        null
    }

    /**
     * Returns the sum of [File.length] for every file in the ride-logs directory.
     * Returns 0 on any I/O error or when the directory is unavailable.
     */
    override fun totalBytes(): Long = try {
        logDirProvider()
            ?.takeIf { it.exists() }
            ?.listFiles()
            ?.sumOf { it.length() }
            ?: 0L
    } catch (_: Exception) {
        0L
    }

    /**
     * Deletes every file in the ride-logs directory.
     *
     * @return count of files deleted, or 0 if the directory was empty or unavailable.
     */
    override fun clear(): Int = try {
        val files = logDirProvider()
            ?.takeIf { it.exists() }
            ?.listFiles()
            ?: return 0
        files.count { it.delete() }
    } catch (_: Exception) {
        0
    }
}
