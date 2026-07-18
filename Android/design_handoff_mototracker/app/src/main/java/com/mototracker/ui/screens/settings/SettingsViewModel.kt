package com.mototracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.core.format.UnitFormatter
import com.mototracker.data.diagnostics.RideLogShareIntentFactory
import com.mototracker.data.diagnostics.RideLogStore
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.repository.BackupRepository
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.SettingsStore
import com.mototracker.domain.backup.RestoreMode
import com.mototracker.ui.state.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Settings screen (B7).
 *
 * Combines persisted settings, the live bike list, and the live route summary list into a
 * single [SettingsUiState] via the pure [build] function. Using the lightweight
 * [RouteSummaryModel] avoids loading GPS trace blobs, which is especially important here
 * since the settings screen only needs scalar fields (synced, km, name, date). All intent
 * methods delegate to [SettingsStore], [BikeRepository], or [SyncRepository].
 *
 * @param settingsStore      Reads and writes all persisted [AppSettings].
 * @param bikeRepository     Provides and mutates the motorcycle list.
 * @param routeRepository    Provides the route summary list (for sync queue + broadcast auto-stats).
 * @param syncRepository     Drains the outbound sync queue.
 * @param rideLogStore       Read-only access to ride-log files (size + delete).
 * @param shareIntentFactory Selects the file to share in the Diagnostics section.
 * @param backupRepository   Handles JSON backup export and import.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val bikeRepository: BikeRepository,
    private val routeRepository: RouteRepository,
    private val syncRepository: SyncRepository,
    private val rideLogStore: RideLogStore,
    private val shareIntentFactory: RideLogShareIntentFactory,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(SettingsTab.ACCOUNT)

    /**
     * Currently selected tab on the Settings screen.
     *
     * In-memory only — not persisted to DataStore. Defaults to [SettingsTab.ACCOUNT].
     */
    val selectedTab: StateFlow<SettingsTab> = _selectedTab.asStateFlow()

    /**
     * Switches the active Settings tab to [tab].
     *
     * @param tab The tab to select.
     */
    fun selectTab(tab: SettingsTab) {
        _selectedTab.value = tab
    }

    private val _rideLogUsedBytes = MutableStateFlow(0L)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _rideLogUsedBytes.value = rideLogStore.totalBytes()
        }
    }

    /** Live UI state for the Settings screen. */
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsStore.settings,
        bikeRepository.observeAll(),
        routeRepository.observeSummaries(),
        _rideLogUsedBytes,
    ) { settings, bikes, summaries, logBytes ->
        build(settings, bikes, summaries, logBytes)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun build(
        settings: AppSettings,
        bikes: List<Bike>,
        summaries: List<RouteSummaryModel>,
        rideLogUsedBytes: Long = 0L,
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
                tankCapacityL = bike.tankCapacityL,
                fuelPricePerL = bike.fuelPricePerL,
                consumptionLper100km = bike.consumptionLper100km,
                autoUpdateConsumption = bike.autoUpdateConsumption,
            )
        }

        val pendingRoutes = summaries
            .filter { !it.synced }
            .map { s ->
                SyncQueueItemUi(
                    routeId = s.id,
                    name = s.name,
                    dateDisplay = dateFormat.format(Date(s.dateEpochMs)),
                    kmDisplay = UnitFormatter.formatDistance(s.km, units),
                )
            }

        val currentBike = bikes.find { it.id == settings.currentBikeId }
        val bcBikeDisplay = currentBike?.let { "${it.name} ${it.year}" } ?: ""

        val today = Calendar.getInstance()
        val todayKm = summaries.sumOf { s ->
            val cal = Calendar.getInstance().apply { timeInMillis = s.dateEpochMs }
            if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            ) s.km else 0.0
        }
        val totalKm = summaries.sumOf { it.km }

        return SettingsUiState(
            bikes = bikeUis,
            currentBikeId = settings.currentBikeId,
            theme = settings.theme,
            accent = settings.accent,
            language = settings.lang,
            units = settings.units,
            serverAddress = settings.serverAddress,
            noInternet = settings.noInternet,
            syncEnabled = settings.syncEnabled,
            pendingRoutes = pendingRoutes,
            bcName = settings.bcName,
            bcPhone = settings.bcPhone,
            bcOrigin = settings.bcOrigin,
            bcSocial = settings.bcSocial,
            bcBikeDisplay = bcBikeDisplay,
            bcTodayDisplay = UnitFormatter.formatDistance(todayKm, units),
            bcTotalDisplay = UnitFormatter.formatDistance(totalKm, units),
            gpsCorrect = settings.gpsCorrect,
            androidAutoEnabled = settings.androidAutoEnabled,
            autoPause = settings.autoPause,
            keepScreenOn = settings.keepScreenOn,
            debugLoggingEnabled = settings.debugLoggingEnabled,
            rideLogUsedBytes = rideLogUsedBytes,
            currency = settings.currency,
            wavesEnabled = settings.wavesEnabled,
            coordFormat = settings.coordFormat,
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
     * Input is validated via [BikeFormValidation.validate]; the call is a no-op when
     * validation returns an error result.
     *
     * @param name                     Display name, e.g. "Yamaha MT-07".
     * @param year                     Model year (must be in 1900–2030).
     * @param plate                    Registration plate (may be blank).
     * @param status                   Lifecycle status; defaults to [BikeStatus.ACTIVE].
     * @param tankCapacityLText        Tank capacity string; blank → null, non-blank must be a non-negative Double.
     * @param fuelPricePerLText        Fuel price string; blank → null, non-blank must be a non-negative Double.
     * @param consumptionLper100kmText Consumption string; blank → null, non-blank must be a non-negative Double.
     * @param autoUpdateConsumption    Checkbox state for auto-update from refuel ledger (K2).
     */
    fun addBike(
        name: String,
        year: Int,
        plate: String,
        status: BikeStatus = BikeStatus.ACTIVE,
        tankCapacityLText: String = "",
        fuelPricePerLText: String = "",
        consumptionLper100kmText: String = "",
        autoUpdateConsumption: Boolean = false,
    ) {
        val result = BikeFormValidation.validate(
            name, year.toString(), plate,
            tankCapacityLText, fuelPricePerLText, consumptionLper100kmText,
            autoUpdateConsumption,
        )
        if (result !is BikeFormResult.Valid) return
        viewModelScope.launch {
            bikeRepository.addBike(
                Bike(
                    id = UUID.randomUUID().toString(),
                    name = result.name,
                    year = result.year,
                    plate = result.plate,
                    status = status,
                    tankCapacityL = result.tankCapacityL,
                    fuelPricePerL = result.fuelPricePerL,
                    consumptionLper100km = result.consumptionLper100km,
                    autoUpdateConsumption = result.autoUpdateConsumption,
                )
            )
        }
    }

    /**
     * Updates an existing motorcycle identified by [id], preserving its UUID (upsert).
     *
     * Input is validated via [BikeFormValidation.validate]; the call is a no-op when
     * validation returns an error result.
     *
     * @param id                       UUID of the bike to update.
     * @param name                     New display name.
     * @param year                     New model year (must be in 1900–2030).
     * @param plate                    New registration plate (may be blank).
     * @param status                   New lifecycle status.
     * @param tankCapacityLText        Tank capacity string; blank → null, non-blank must be a non-negative Double.
     * @param fuelPricePerLText        Fuel price string; blank → null, non-blank must be a non-negative Double.
     * @param consumptionLper100kmText Consumption string; blank → null, non-blank must be a non-negative Double.
     * @param autoUpdateConsumption    Checkbox state for auto-update from refuel ledger (K2).
     */
    fun updateBike(
        id: String,
        name: String,
        year: Int,
        plate: String,
        status: BikeStatus,
        tankCapacityLText: String = "",
        fuelPricePerLText: String = "",
        consumptionLper100kmText: String = "",
        autoUpdateConsumption: Boolean = false,
    ) {
        val result = BikeFormValidation.validate(
            name, year.toString(), plate,
            tankCapacityLText, fuelPricePerLText, consumptionLper100kmText,
            autoUpdateConsumption,
        )
        if (result !is BikeFormResult.Valid) return
        viewModelScope.launch {
            bikeRepository.addBike(
                Bike(
                    id = id,
                    name = result.name,
                    year = result.year,
                    plate = result.plate,
                    status = status,
                    tankCapacityL = result.tankCapacityL,
                    fuelPricePerL = result.fuelPricePerL,
                    consumptionLper100km = result.consumptionLper100km,
                    autoUpdateConsumption = result.autoUpdateConsumption,
                )
            )
        }
    }

    /** Persists the ISO 4217 currency code used for fuel cost display. */
    fun setCurrency(currency: String) {
        viewModelScope.launch { settingsStore.setCurrency(currency) }
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

    /** Persists the master network kill-switch flag (U1). */
    fun setNoInternet(value: Boolean) {
        viewModelScope.launch { settingsStore.setNoInternet(value) }
    }

    /**
     * Persists the sync-enabled flag (U1).
     *
     * No-op when [AppSettings.noInternet] is `true` — the master kill-switch must be cleared
     * first before sync can be re-enabled.
     */
    fun setSyncEnabled(value: Boolean) {
        viewModelScope.launch {
            if (settingsStore.settings.first().noInternet) return@launch
            settingsStore.setSyncEnabled(value)
        }
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

    /** Persists the BLE waves (pomachania) enabled flag. */
    fun setWavesEnabled(value: Boolean) {
        viewModelScope.launch { settingsStore.setWavesEnabled(value) }
    }

    /** Persists the diagnostic ride-logging enabled flag. */
    fun setDebugLogging(value: Boolean) {
        viewModelScope.launch { settingsStore.setDebugLoggingEnabled(value) }
    }

    /**
     * Persists the coordinate display format key (P2).
     *
     * @param key One of "dd", "dms", or "utm".
     */
    fun setCoordFormat(key: String) {
        viewModelScope.launch { settingsStore.setCoordFormat(key) }
    }

    /**
     * Deletes all files in the ride-logs directory and refreshes [SettingsUiState.rideLogUsedBytes].
     */
    fun clearRideLogs() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rideLogStore.clear()
                _rideLogUsedBytes.value = rideLogStore.totalBytes()
            }
        }
    }

    /**
     * Returns the latest ride-log file suitable for sharing via [shareIntentFactory], or null
     * when no log exists. The Composable uses the returned [File] to obtain a `content://` URI
     * via `FileProvider.getUriForFile` and launches `Intent.ACTION_SEND`.
     */
    fun getShareTargetFile(): File? = shareIntentFactory.shareTargetFile(rideLogStore)

    // ── Backup / restore (B16) ────────────────────────────────────────────────

    /**
     * One-shot events emitted after a restore completes (success or failure).
     * The Composable collects this to show a Toast.
     */
    private val _restoreEvent = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)

    /** Observed by the Composable to display restore outcome toasts. */
    val restoreEvent: SharedFlow<Result<Unit>> = _restoreEvent.asSharedFlow()

    /**
     * Serialises all local data to a JSON string on [Dispatchers.IO].
     *
     * The Composable calls this, receives the string, and writes it to the SAF
     * [OutputStream] itself — keeping file I/O out of the ViewModel.
     *
     * @return [Result.success] with the JSON payload, or [Result.failure] on error.
     */
    suspend fun buildBackup(): Result<String> = withContext(Dispatchers.IO) {
        backupRepository.exportBackup()
    }

    /**
     * Parses [json] and merges or replaces local data according to [mode], then emits
     * a [Result] on [restoreEvent] for the Composable to display as a toast.
     *
     * Runs on [Dispatchers.IO] so Room writes are main-safe.
     *
     * @param json Raw backup JSON string read from a SAF [InputStream].
     * @param mode [RestoreMode.MERGE] or [RestoreMode.REPLACE].
     */
    fun restore(json: String, mode: RestoreMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = backupRepository.importBackup(json, mode)
            _restoreEvent.emit(result.map { })
        }
    }
}
