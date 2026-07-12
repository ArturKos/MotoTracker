package com.mototracker.domain.backup

import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.settings.AppSettings

/**
 * Carry of routes/bikes to persist and the optional settings to apply.
 *
 * @param routes         Final route list to upsert (MERGE) or replace (REPLACE).
 * @param bikes          Final bike list to upsert (MERGE) or replace (REPLACE).
 * @param settings       Settings to apply; `null` when [RestoreMode.MERGE] (leave unchanged).
 * @param addedRoutes    Count of routes not previously present.
 * @param updatedRoutes  Count of routes that overwrote an existing row by UUID.
 * @param addedBikes     Count of bikes not previously present.
 * @param updatedBikes   Count of bikes that overwrote an existing row by UUID.
 */
data class BackupResult(
    val routes: List<Route>,
    val bikes: List<Bike>,
    val settings: AppSettings?,
    val addedRoutes: Int,
    val updatedRoutes: Int,
    val addedBikes: Int,
    val updatedBikes: Int,
)

/**
 * Pure domain function that computes the merge/replace outcome.
 *
 * Entirely free of Android, Room, or IO dependencies — deterministic and unit-testable.
 */
object BackupMerger {

    /**
     * Computes the final set of routes, bikes, and optional settings to persist.
     *
     * **MERGE** mode:
     * - Builds a union of existing + imported items keyed by UUID.
     * - When the same UUID exists in both, the imported item wins.
     * - Settings are left unchanged (`BackupResult.settings` is `null`).
     *
     * **REPLACE** mode:
     * - The imported set entirely replaces all existing data (caller must delete all first).
     * - Imported settings are applied (`BackupResult.settings` is non-null).
     *
     * @param existingRoutes    Routes currently stored locally.
     * @param existingBikes     Bikes currently stored locally.
     * @param currentSettings   Current persisted settings (unused in REPLACE, returned
     *                          as-is for MERGE callers that just want counts).
     * @param imported          The decoded [BackupData] to incorporate.
     * @param mode              Controls how conflicts are resolved.
     * @return [BackupResult] with the final lists and change counts.
     */
    fun merge(
        existingRoutes: List<Route>,
        existingBikes: List<Bike>,
        currentSettings: AppSettings,
        imported: BackupData,
        mode: RestoreMode,
    ): BackupResult {
        return when (mode) {
            RestoreMode.MERGE -> doMerge(existingRoutes, existingBikes, imported)
            RestoreMode.REPLACE -> doReplace(imported)
        }
    }

    private fun doMerge(
        existingRoutes: List<Route>,
        existingBikes: List<Bike>,
        imported: BackupData,
    ): BackupResult {
        val existingRouteMap = existingRoutes.associateBy { it.id }
        val existingBikeMap = existingBikes.associateBy { it.id }

        var addedRoutes = 0
        var updatedRoutes = 0
        var addedBikes = 0
        var updatedBikes = 0

        // Start with existing, then overlay imported (imported wins on collision)
        val mergedRouteMap = existingRouteMap.toMutableMap()
        for (route in imported.routes) {
            if (mergedRouteMap.containsKey(route.id)) updatedRoutes++ else addedRoutes++
            mergedRouteMap[route.id] = route
        }

        val mergedBikeMap = existingBikeMap.toMutableMap()
        for (bike in imported.bikes) {
            if (mergedBikeMap.containsKey(bike.id)) updatedBikes++ else addedBikes++
            mergedBikeMap[bike.id] = bike
        }

        return BackupResult(
            routes = mergedRouteMap.values.toList(),
            bikes = mergedBikeMap.values.toList(),
            settings = null,
            addedRoutes = addedRoutes,
            updatedRoutes = updatedRoutes,
            addedBikes = addedBikes,
            updatedBikes = updatedBikes,
        )
    }

    private fun doReplace(imported: BackupData): BackupResult = BackupResult(
        routes = imported.routes,
        bikes = imported.bikes,
        settings = imported.settings,
        addedRoutes = imported.routes.size,
        updatedRoutes = 0,
        addedBikes = imported.bikes.size,
        updatedBikes = 0,
    )
}
