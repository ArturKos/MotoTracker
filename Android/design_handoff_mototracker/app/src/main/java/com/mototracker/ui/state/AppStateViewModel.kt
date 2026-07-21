package com.mototracker.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.i18n.LocaleController
import com.mototracker.data.auth.AuthState
import com.mototracker.data.auth.AuthStateStore
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.network.SessionStore
import com.mototracker.data.terms.TermsAcceptanceStore
import com.mototracker.data.repository.RoutePreloadCache
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.ui.navigation.SyncState
import com.mototracker.ui.navigation.deriveSyncState
import com.mototracker.ui.navigation.isRecordingLocked
import com.mototracker.di.IoDispatcher
import com.mototracker.ui.theme.AccentColor
import com.mototracker.ui.theme.MotoTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-level ViewModel holding the single source of truth for global UI state.
 *
 * Exposed as a unidirectional [StateFlow]: consumers observe [uiState] and trigger updates only
 * via the named intent methods below. Internal state is never mutated directly from outside.
 *
 * [localeController] is injected to keep this ViewModel testable without an Android runtime;
 * fakes replace it in unit tests.
 *
 * [recordingBridge] derives [recordingActive] without coupling to the nav-scoped
 * [com.mototracker.ui.screens.record.RecordingViewModel].
 *
 * [authStateStore] and [sessionStore] persist the onboarding/auth choice and the server
 * session cookie so the Login screen is skipped on subsequent launches (B22).
 *
 * [termsStore] persists the first-launch terms acceptance flag; the gate is shown until
 * the user accepts (J2).
 *
 * [networkMonitor], [settingsSource], and [syncRepository] are combined to derive
 * [syncState] — the live [SyncState] shown in the top-app-bar sync chip (D9).
 *
 * @param localeController  Applies per-app locale changes.
 * @param recordingBridge   Source of the current recording phase for [recordingActive].
 * @param authStateStore    Persists and reads the onboarding/auth choice.
 * @param sessionStore      Persists and reads the server session cookie.
 * @param termsStore        Persists and reads the first-launch terms acceptance flag.
 * @param networkMonitor    Observes live network connectivity for [syncState].
 * @param settingsSource    Reads [AppSettings.noInternet] for [syncState] (U1).
 * @param syncRepository    Provides the pending sync queue count for [syncState].
 * @param routeRepository   Source for [preloadedRoutes]; queried eagerly during splash (AE4).
 * @param routePreloadCache Singleton cache written by [preloadedRoutes] and read by
 *                          [com.mototracker.ui.screens.routes.RoutesViewModel] as its initial value.
 * @param ioDispatcher      Dispatcher for the [preloadedRoutes] upstream (Room query + seed
 *                          side-effect); defaults to [kotlinx.coroutines.Dispatchers.IO] in
 *                          production, replaced with a test dispatcher in unit tests (AF2).
 */
@HiltViewModel
class AppStateViewModel @Inject constructor(
    private val localeController: LocaleController,
    private val recordingBridge: CarRecordingBridge,
    private val authStateStore: AuthStateStore,
    private val sessionStore: SessionStore,
    private val termsStore: TermsAcceptanceStore,
    private val networkMonitor: NetworkMonitor,
    private val settingsSource: AppSettingsSource,
    private val syncRepository: SyncRepository,
    private val routeRepository: RouteRepository,
    private val routePreloadCache: RoutePreloadCache,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())

    /** Read-only view of the global UI state; never null, never throws. */
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    /**
     * Emits `true` whenever a recording session is active (phase ≠ Idle).
     *
     * Derived from [CarRecordingBridge.phase] via [isRecordingLocked]. Consumers
     * (e.g. [com.mototracker.MainActivity]) use this to lock bottom-nav tab switching and block
     * the back gesture while a ride is in progress.
     */
    val recordingActive: StateFlow<Boolean> = recordingBridge.phase
        .map { isRecordingLocked(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Live [SyncState] to display in the top-app-bar sync chip (D9).
     *
     * Combines three independent sources — network connectivity, user settings, and the
     * pending sync-queue count — via [deriveSyncState]. Starts eagerly so the chip is
     * populated before the first frame.
     */
    val syncState: StateFlow<SyncState> = combine(
        networkMonitor.isOnline,
        settingsSource.settings,
        syncRepository.pendingCount,
    ) { online, s, pending ->
        deriveSyncState(online, s.noInternet, pending)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SyncState.Offline)

    /**
     * Eagerly-started stream of route summaries, warmed during the splash animation (AE4).
     *
     * Because this ViewModel is activity-scoped and composed as soon as the splash renders,
     * [SharingStarted.Eagerly] causes Room to evaluate [RouteRepository.observeSummaries] during
     * the ~2.2 s splash window. Each emission is forwarded to [routePreloadCache] so that
     * [com.mototracker.ui.screens.routes.RoutesViewModel] can read the cached snapshot as its
     * [kotlinx.coroutines.flow.stateIn] initial value, populating the Routes tab instantly.
     *
     * The upstream — the Room query and the [routePreloadCache] seed side-effect — runs on
     * [ioDispatcher] (production: [kotlinx.coroutines.Dispatchers.IO]) so it cannot block the
     * main thread during the first splash frame (AF2).
     *
     * **Does NOT gate splash dismissal.** The splash timing is controlled exclusively by
     * [com.mototracker.ui.screens.splash.SplashChoreography.TOTAL_MS] and [startupDecision].
     */
    val preloadedRoutes: StateFlow<List<RouteSummaryModel>> = routeRepository.observeSummaries()
        .onEach { routePreloadCache.seed(it) }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * One-time startup navigation decision, updated whenever the persisted auth state, session
     * cookie, or terms acceptance flag changes.
     *
     * Emits [StartupDecision.Loading] until all three sources produce their first value, then emits
     * [StartupDecision.Ready] with the resolved [AppScreen] start destination, current auth flag,
     * optional session-expired flag, and the terms acceptance gate flag.
     */
    val startupDecision: StateFlow<StartupDecision> = combine(
        authStateStore.authState,
        sessionStore.session,
        termsStore.accepted,
    ) { authState, session, termsAccepted ->
        StartupDecision.Ready(
            startScreen = startScreenFor(authState, session.isAuthenticated),
            authed = authState == AuthState.AUTHED && session.isAuthenticated,
            sessionExpired = authState == AuthState.AUTHED && !session.isAuthenticated,
            termsAccepted = termsAccepted,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StartupDecision.Loading)

    /**
     * Persists the user's acceptance of the first-launch terms/disclaimer (J2).
     *
     * Called when the user taps Accept on the [com.mototracker.ui.screens.terms.TermsScreen].
     * Declining is handled in the Activity by calling [android.app.Activity.finishAndRemoveTask];
     * no state write is needed for decline since absent terms flag = not accepted.
     */
    fun acceptTerms() {
        viewModelScope.launch { termsStore.setAccepted(true) }
    }

    /**
     * Marks the user as authenticated and navigates to the main app shell.
     *
     * Persists [AuthState.AUTHED] and updates [uiState] in-memory so the theme/nav shell
     * reflects the change immediately.
     */
    fun signIn() {
        _uiState.value = _uiState.value.copy(authed = true, screen = AppScreen.MAIN)
        viewModelScope.launch { authStateStore.set(AuthState.AUTHED) }
    }

    /**
     * Enters the app without authentication (guest / local-only mode).
     *
     * Persists [AuthState.GUEST]. No server sync is attempted for guest sessions.
     */
    fun continueAsGuest() {
        _uiState.value = _uiState.value.copy(authed = false, screen = AppScreen.MAIN)
        viewModelScope.launch { authStateStore.set(AuthState.GUEST) }
    }

    /**
     * Signs out and resets all state to defaults, returning to the login screen.
     *
     * Persists [AuthState.NONE], clears the session cookie, and reverts [uiState] to its
     * defaults (login screen, unauthenticated).
     */
    fun signOut() {
        _uiState.value = AppUiState()
        viewModelScope.launch {
            authStateStore.set(AuthState.NONE)
            sessionStore.clear()
        }
    }

    /**
     * Changes the active UI language and applies it via [localeController].
     *
     * @param language The [Language] to activate.
     */
    fun setLanguage(language: Language) {
        _uiState.value = _uiState.value.copy(language = language)
        localeController.applyLanguage(language.tag)
    }

    /**
     * Changes the active visual theme (cockpit / grid / light).
     *
     * @param theme The [MotoTheme] to activate.
     */
    fun setTheme(theme: MotoTheme) {
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    /**
     * Overrides the accent colour for the current theme.
     *
     * @param accent The [AccentColor] to activate.
     */
    fun setAccent(accent: AccentColor) {
        _uiState.value = _uiState.value.copy(accent = accent)
    }

    /**
     * Changes the measurement system preference (metric / imperial).
     *
     * @param units The [Units] to activate.
     */
    fun setUnits(units: Units) {
        _uiState.value = _uiState.value.copy(units = units)
    }
}
