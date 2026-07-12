package com.mototracker.domain.backup

import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.settings.AppSettings

/**
 * Versioned container for a full local-data backup.
 *
 * @param schemaVersion Monotonically increasing schema number. Decode rejects any version
 *                      greater than [CURRENT_SCHEMA_VERSION] so an old app will not silently
 *                      corrupt data from a newer backup.
 * @param routes        All recorded routes at the time of export.
 * @param bikes         All motorcycles at the time of export.
 * @param settings      A snapshot of all application settings.
 */
data class BackupData(
    val schemaVersion: Int,
    val routes: List<Route>,
    val bikes: List<Bike>,
    val settings: AppSettings,
) {
    companion object {
        /** Schema version written by [BackupSerializer.encode] and validated by [BackupSerializer.decode]. */
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/**
 * How imported data should be merged with existing local data.
 *
 * - [MERGE] preserves existing data and settings; imported items win on UUID collision.
 * - [REPLACE] discards all existing routes and bikes and overwrites settings.
 */
enum class RestoreMode { MERGE, REPLACE }

/**
 * Summary counts returned by [BackupRepositoryImpl.importBackup].
 *
 * @param addedRoutes   Routes inserted (no prior row with that UUID).
 * @param updatedRoutes Routes whose existing row was overwritten (UUID collision).
 * @param addedBikes    Bikes inserted (no prior row with that UUID).
 * @param updatedBikes  Bikes whose existing row was overwritten (UUID collision).
 */
data class ImportSummary(
    val addedRoutes: Int,
    val updatedRoutes: Int,
    val addedBikes: Int,
    val updatedBikes: Int,
)
