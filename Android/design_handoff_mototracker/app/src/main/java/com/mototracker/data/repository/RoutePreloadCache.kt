package com.mototracker.data.repository

import com.mototracker.data.model.RouteSummaryModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton cache that stores the most recently observed route summaries.
 *
 * [com.mototracker.ui.state.AppStateViewModel] seeds this cache eagerly during splash via
 * its [preloadedRoutes][com.mototracker.ui.state.AppStateViewModel.preloadedRoutes] flow.
 * [com.mototracker.ui.screens.routes.RoutesViewModel] reads [routes] at construction time as
 * the `stateIn` initial value, so the Routes tab is populated on the first frame with no spinner.
 *
 * Thread-safe: [MutableStateFlow] guarantees atomic reads and writes.
 */
@Singleton
class RoutePreloadCache @Inject constructor() {

    private val _routes = MutableStateFlow<List<RouteSummaryModel>>(emptyList())

    /** Latest snapshot of route summaries; [emptyList] until first seed from [AppStateViewModel]. */
    val routes: StateFlow<List<RouteSummaryModel>> = _routes.asStateFlow()

    /**
     * Replaces the cached list with [routes].
     *
     * Called on every emission of [AppStateViewModel.preloadedRoutes] — typically once after the
     * initial Room query resolves and again whenever the database changes.
     *
     * @param routes The latest route summaries from Room.
     */
    fun seed(routes: List<RouteSummaryModel>) {
        _routes.value = routes
    }
}
