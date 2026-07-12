package com.mototracker.data.repository

import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.SettingsStore
import com.mototracker.domain.backup.BackupData
import com.mototracker.domain.backup.BackupMerger
import com.mototracker.domain.backup.BackupSerializer
import com.mototracker.domain.backup.ImportSummary
import com.mototracker.domain.backup.RestoreMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room + DataStore implementation of [BackupRepository].
 *
 * Snapshot reads use `.first()` so a single consistent value is captured even though
 * the underlying flows are live. All writes run on whatever dispatcher the caller
 * provides; callers should invoke from `Dispatchers.IO`.
 *
 * @param routeRepository  Provides and persists routes.
 * @param bikeRepository   Provides and persists bikes.
 * @param settingsStore    Provides and persists application settings.
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val routeRepository: RouteRepository,
    private val bikeRepository: BikeRepository,
    private val settingsStore: SettingsStore,
) : BackupRepository {

    /**
     * Captures a point-in-time snapshot of all local data and serialises it to JSON.
     *
     * @return [Result.success] with the JSON string, or [Result.failure] if serialisation throws.
     */
    override suspend fun exportBackup(): Result<String> = runCatching {
        val routes = routeRepository.observeAll().first()
        val bikes = bikeRepository.observeAll().first()
        val settings = settingsStore.settings.first()
        val data = BackupData(
            schemaVersion = BackupData.CURRENT_SCHEMA_VERSION,
            routes = routes,
            bikes = bikes,
            settings = settings,
        )
        BackupSerializer.encode(data)
    }

    /**
     * Decodes [json], merges or replaces local data according to [mode], then persists the result.
     *
     * **MERGE:** upserts imported routes and bikes (imported wins on UUID collision);
     * settings are left unchanged.
     *
     * **REPLACE:** calls [RouteRepository.deleteAll] and [BikeRepository.deleteAll] first,
     * then upserts all imported items, then applies all imported settings.
     *
     * @param json Raw backup JSON.
     * @param mode Merge or replace strategy.
     * @return [Result.success] with an [ImportSummary], or [Result.failure] on any error.
     */
    override suspend fun importBackup(json: String, mode: RestoreMode): Result<ImportSummary> =
        runCatching {
            val imported = BackupSerializer.decode(json).getOrThrow()
            val currentRoutes = routeRepository.observeAll().first()
            val currentBikes = bikeRepository.observeAll().first()
            val currentSettings = settingsStore.settings.first()

            val result = BackupMerger.merge(
                existingRoutes = currentRoutes,
                existingBikes = currentBikes,
                currentSettings = currentSettings,
                imported = imported,
                mode = mode,
            )

            if (mode == RestoreMode.REPLACE) {
                routeRepository.deleteAll()
                bikeRepository.deleteAll()
            }

            for (route in result.routes) routeRepository.save(route)
            for (bike in result.bikes) bikeRepository.addBike(bike)

            result.settings?.let { applySettings(it) }

            ImportSummary(
                addedRoutes = result.addedRoutes,
                updatedRoutes = result.updatedRoutes,
                addedBikes = result.addedBikes,
                updatedBikes = result.updatedBikes,
            )
        }

    private suspend fun applySettings(s: AppSettings) {
        settingsStore.setOffline(s.offline)
        settingsStore.setAutoSync(s.autoSync)
        settingsStore.setOfflineOnly(s.offlineOnly)
        settingsStore.setGpsCorrect(s.gpsCorrect)
        settingsStore.setCurrentBikeId(s.currentBikeId)
        settingsStore.setServerAddress(s.serverAddress)
        settingsStore.setUnits(s.units)
        settingsStore.setTheme(s.theme)
        settingsStore.setAccent(s.accent)
        settingsStore.setLang(s.lang)
        settingsStore.setAutoPause(s.autoPause)
        settingsStore.setKeepScreenOn(s.keepScreenOn)
        settingsStore.setAndroidAutoEnabled(s.androidAutoEnabled)
        settingsStore.setBcName(s.bcName)
        settingsStore.setBcPhone(s.bcPhone)
        settingsStore.setBcOrigin(s.bcOrigin)
        settingsStore.setBcSocial(s.bcSocial)
        settingsStore.setDebugLoggingEnabled(s.debugLoggingEnabled)
        settingsStore.setOsrmBaseUrl(s.osrmBaseUrl)
    }
}
