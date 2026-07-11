package com.mototracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.core.format.UnitFormatter
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.SettingsStore
import com.mototracker.ui.state.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Settings screen (B7).
 *
 * Combines persisted settings, the live bike list, and the live route list into a
 * single [SettingsUiState] via the pure [build] function. All intent methods
 * delegate to [SettingsStore], [BikeRepository], or [SyncRepository] — never to UI.
 *
 * @param settingsStore   Reads and writes all persisted [AppSettings].
 * @param bikeRepository  Provides and mutates the motorcycle list.
 * @param routeRepository Provides the route list (for sync queue + broadcast auto-stats).
 * @param syncRepository  Drains the outbound sync queue.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val bikeRepository: BikeRepository,
    private val routeRepository: RouteRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    /** Live UI state for the Settings screen. */
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsStore.settings,
        bikeRepository.observeAll(),
        routeRepository.observeAll(),
    ) { settings, bikes, routes ->
        build(settings, bikes, routes)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun build(
        settings: AppSettings,
        bikes: List<Bike>,
        routes: List<Route>,
    ): SettingsUiState {
        val units = if (settings.units == "imperial") Units.IMPERIAL else Units.METRIC
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        val bikeUis = bikes.map { bike ->
            BikeUi(
                id = bike.id,
                name = bike.name,
                yearPlate = "${bike.year} · ${bike.plate}",
                status = bike.status,
                isCurrent = bike.id == settings.currentBikeId,
                year = bike.year,
                plate = bike.plate,
            )
        }

        val pendingRoutes = routes
            .filter { !it.synced }
            .map { route ->
                SyncQueueItemUi(
                    routeId = route.id,
                    name = route.name,
                    dateDisplay = dateFormat.format(Date(route.dateEpochMs)),
                    kmDisplay = UnitFormatter.formatDistance(route.km, units),
                )
            }

        val currentBike = bikes.find { it.id == settings.currentBikeId }
        val bcBikeDisplay = currentBike?.let { "${it.name} ${it.year}" } ?: ""

        val today = Calendar.getInstance()
        val todayKm = routes.sumOf { route ->
            val cal = Calendar.getInstance().apply { timeInMillis = route.dateEpochMs }
            if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            ) route.km else 0.0
        }
        val totalKm = routes.sumOf { it.km }

        return SettingsUiState(
            bikes = bikeUis,
            currentBikeId = settings.currentBikeId,
            theme = settings.theme,
            accent = settings.accent,
            language = settings.lang,
            units = settings.units,
            serverAddress = settings.serverAddress,
            offline = settings.offline,
            autoSync = settings.autoSync,
            pendingRoutes = pendingRoutes,
            bcName = settings.bcName,
            bcPhone = settings.bcPhone,
            bcOrigin = settings.bcOrigin,
            bcSocial = settings.bcSocial,
            bcBikeDisplay = bcBikeDisplay,
            bcTodayDisplay = UnitFormatter.formatDistance(todayKm, units),
            bcTotalDisplay = UnitFormatter.formatDistance(totalKm, units),
            offlineOnly = settings.offlineOnly,
            gpsCorrect = settings.gpsCorrect,
            androidAutoEnabled = settings.androidAutoEnabled,
            autoPause = settings.autoPause,
            keepScreenOn = settings.keepScreenOn,
        )
    }

    // ── Intents ───────────────────────────────────────────────────────────────

    /** Sets [bikeId] as the currently active motorcycle. */
    fun selectBike(bikeId: String) {
        viewModelScope.launch { settingsStore.setCurrentBikeId(bikeId) }
    }

    /**
     * Adds a new motorcycle with a generated UUID.
     *
     * Input is validated via [BikeFormValidation.validate]; the call is a no-op on
     * [BikeFormResult.NameBlank] or [BikeFormResult.YearInvalid].
     *
     * @param name   Display name, e.g. "Yamaha MT-07".
     * @param year   Model year (must be in 1900–2030).
     * @param plate  Registration plate (may be blank).
     * @param status Lifecycle status; defaults to [BikeStatus.ACTIVE].
     */
    fun addBike(
        name: String,
        year: Int,
        plate: String,
        status: BikeStatus = BikeStatus.ACTIVE,
    ) {
        val result = BikeFormValidation.validate(name, year.toString(), plate)
        if (result !is BikeFormResult.Valid) return
        viewModelScope.launch {
            bikeRepository.addBike(
                Bike(
                    id = UUID.randomUUID().toString(),
                    name = result.name,
                    year = result.year,
                    plate = result.plate,
                    status = status,
                )
            )
        }
    }

    /**
     * Updates an existing motorcycle identified by [id], preserving its UUID (upsert).
     *
     * Input is validated via [BikeFormValidation.validate]; the call is a no-op on
     * [BikeFormResult.NameBlank] or [BikeFormResult.YearInvalid].
     *
     * @param id     UUID of the bike to update.
     * @param name   New display name.
     * @param year   New model year (must be in 1900–2030).
     * @param plate  New registration plate (may be blank).
     * @param status New lifecycle status.
     */
    fun updateBike(
        id: String,
        name: String,
        year: Int,
        plate: String,
        status: BikeStatus,
    ) {
        val result = BikeFormValidation.validate(name, year.toString(), plate)
        if (result !is BikeFormResult.Valid) return
        viewModelScope.launch {
            bikeRepository.addBike(
                Bike(
                    id = id,
                    name = result.name,
                    year = result.year,
                    plate = result.plate,
                    status = status,
                )
            )
        }
    }

    /** Persists the visual theme key ("cockpit", "grid", or "light"). */
    fun setTheme(theme: String) {
        viewModelScope.launch { settingsStore.setTheme(theme) }
    }

    /** Persists the accent colour hex string. */
    fun setAccent(accent: String) {
        viewModelScope.launch { settingsStore.setAccent(accent) }
    }

    /** Persists the BCP-47 language tag. */
    fun setLanguage(lang: String) {
        viewModelScope.launch { settingsStore.setLang(lang) }
    }

    /** Persists the measurement unit ("metric" or "imperial"). */
    fun setUnits(units: String) {
        viewModelScope.launch { settingsStore.setUnits(units) }
    }

    /** Persists the GPStrack server address. */
    fun setServerAddress(address: String) {
        viewModelScope.launch { settingsStore.setServerAddress(address) }
    }

    /** Persists the offline mode flag. */
    fun setOffline(value: Boolean) {
        viewModelScope.launch { settingsStore.setOffline(value) }
    }

    /** Persists the auto-sync flag. */
    fun setAutoSync(value: Boolean) {
        viewModelScope.launch { settingsStore.setAutoSync(value) }
    }

    /** Triggers an immediate upload of all pending sync queue entries. */
    fun syncNow() {
        viewModelScope.launch { syncRepository.syncNow() }
    }

    /**
     * Persists all four editable fields of the Bluetooth broadcast profile.
     *
     * @param name   Rider name or handle.
     * @param phone  Rider phone number.
     * @param origin Riding-from city.
     * @param social Social media handle.
     */
    fun saveBroadcastProfile(name: String, phone: String, origin: String, social: String) {
        viewModelScope.launch {
            settingsStore.setBcName(name)
            settingsStore.setBcPhone(phone)
            settingsStore.setBcOrigin(origin)
            settingsStore.setBcSocial(social)
        }
    }

    /** Persists the work-without-internet flag. */
    fun setOfflineOnly(value: Boolean) {
        viewModelScope.launch { settingsStore.setOfflineOnly(value) }
    }

    /** Persists the GPS road-correction flag. */
    fun setGpsCorrect(value: Boolean) {
        viewModelScope.launch { settingsStore.setGpsCorrect(value) }
    }

    /** Persists the Android Auto enabled flag. */
    fun setAndroidAutoEnabled(value: Boolean) {
        viewModelScope.launch { settingsStore.setAndroidAutoEnabled(value) }
    }

    /** Persists the auto-pause preference. */
    fun setAutoPause(value: Boolean) {
        viewModelScope.launch { settingsStore.setAutoPause(value) }
    }

    /** Persists the keep-screen-on preference. */
    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch { settingsStore.setKeepScreenOn(value) }
    }
}
