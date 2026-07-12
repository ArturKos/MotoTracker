package com.mototracker.data.repository

import com.mototracker.domain.backup.ImportSummary
import com.mototracker.domain.backup.RestoreMode

/**
 * Persistence contract for JSON backup export and import (B16).
 *
 * Implementations snapshot all local data (routes, bikes, settings) into a single JSON
 * string for export, and hydrate local storage from that string on import.
 *
 * Both operations are suspend functions because they touch Room DAOs and DataStore.
 */
interface BackupRepository {

    /**
     * Exports all local routes, bikes, and settings as a single JSON string.
     *
     * @return [Result.success] with the JSON payload, or [Result.failure] on any IO error.
     */
    suspend fun exportBackup(): Result<String>

    /**
     * Imports routes, bikes, and (for [RestoreMode.REPLACE]) settings from [json].
     *
     * @param json Raw backup JSON produced by [exportBackup].
     * @param mode How to merge the imported data with existing local data.
     * @return [Result.success] with an [ImportSummary], or [Result.failure] if the JSON is
     *         malformed, the schema version is unsupported, or a database write fails.
     */
    suspend fun importBackup(json: String, mode: RestoreMode): Result<ImportSummary>
}
