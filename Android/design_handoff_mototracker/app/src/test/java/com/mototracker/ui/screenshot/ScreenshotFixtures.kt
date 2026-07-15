package com.mototracker.ui.screenshot

import com.mototracker.core.format.WeatherUi
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.FeedType
import com.mototracker.domain.recording.RecordingMetrics
import com.mototracker.ui.screens.detail.MeetingUi
import com.mototracker.ui.screens.detail.RouteDetailUiState
import com.mototracker.ui.screens.detail.StatTileUi
import com.mototracker.ui.screens.detail.TrackView
import com.mototracker.ui.screens.login.LoginUiState
import com.mototracker.ui.screens.record.RecordingPhase
import com.mototracker.ui.screens.record.RecordingUiState
import com.mototracker.ui.screens.record.WeatherInfo
import com.mototracker.ui.screens.riders.FeedDotColor
import com.mototracker.ui.screens.riders.FeedEventUi
import com.mototracker.ui.screens.riders.GroupMemberUi
import com.mototracker.ui.screens.riders.RidersUiState
import com.mototracker.ui.screens.riders.WaveUi
import com.mototracker.ui.screens.routes.RouteCardUi
import com.mototracker.ui.screens.routes.RoutesUiState
import com.mototracker.ui.screens.settings.BikeUi
import com.mototracker.ui.screens.settings.SettingsUiState
import com.mototracker.ui.screens.settings.SyncQueueItemUi
import com.mototracker.ui.screens.stats.MonthBarUi
import com.mototracker.ui.screens.stats.RidingStyleUi
import com.mototracker.ui.screens.stats.StatsUiState

/**
 * Pre-built UI state fixtures for JVM screenshot tests.
 *
 * Each fixture has a POPULATED variant (representative data) and, where applicable,
 * an EMPTY variant (no-data / loading / not-found state). Fixtures are intentionally
 * static — no ViewModel, no coroutines, no Android context.
 */
object ScreenshotFixtures {

    // ── Login ─────────────────────────────────────────────────────────────────

    /** Login screen with server address pre-filled and form ready to submit. */
    val loginPopulated = LoginUiState(
        serverAddress = "http://192.168.1.145/gpstrack",
        email = "rider@example.com",
        canSubmit = true,
        loading = false,
        errorMessage = null,
    )

    // ── Recording ─────────────────────────────────────────────────────────────

    /**
     * Idle recording screen with tank capacity configured so [FuelTankRow] renders.
     *
     * Used by the anti-clipping regression guard (G2): the start control must be on-screen on a
     * P20-class (w411dp-h781dp) display. The fill-to-full action is NOT shown in Idle — it
     * only appears in the control strip during Recording and Paused phases.
     */
    val recordingIdle = RecordingUiState(
        phase = RecordingPhase.Idle,
        metrics = RecordingMetrics(
            tankCapacityL = 17.5,
            remainingFuelL = 12.3,
            remainingRangeKm = 210.0,
        ),
        gpsSatCount = 0,
        gpsOnRoad = false,
        weather = null,
    )

    /**
     * Active recording session with a fuel tank configured and price set.
     *
     * Used by the control-strip regression guard (G2): verifies that pause, stop, AND the
     * fill-to-full control are all displayed in the Recording phase when [metrics.tankCapacityL]
     * is non-null. Also exercises the fuel cost readout tile.
     */
    val recordingWithFuelTank = RecordingUiState(
        phase = RecordingPhase.Recording,
        metrics = RecordingMetrics(
            distanceKm = 20.0,
            durationSec = 1200L,
            currentSpeedKmh = 65.0,
            fuelL = 0.9,
            tankCapacityL = 15.0,
            remainingFuelL = 14.1,
            remainingRangeKm = 185.0,
        ),
        gpsSatCount = 8,
        gpsOnRoad = false,
        weather = null,
        fuelPricePerL = 6.89,
        currency = "PLN",
    )

    /**
     * Paused recording session without a tank configured.
     *
     * Used by the H2 regression guard: FILL_TO_FULL must appear in Paused unconditionally,
     * even when [RecordingMetrics.tankCapacityL] is null.
     */
    val recordingPaused = RecordingUiState(
        phase = RecordingPhase.Paused,
        metrics = RecordingMetrics(
            distanceKm = 35.0,
            durationSec = 2400L,
            currentSpeedKmh = 0.0,
        ),
        gpsSatCount = 9,
        gpsOnRoad = false,
        weather = null,
    )

    /** Active recording session with typical mid-ride metrics. */
    val recordingPopulated = RecordingUiState(
        phase = RecordingPhase.Recording,
        metrics = RecordingMetrics(
            distanceKm = 42.7,
            durationSec = 3720L,
            currentSpeedKmh = 87.0,
            avgSpeedKmh = 68.5,
            maxSpeedKmh = 142.0,
            currentLeanDeg = 22.0,
            maxLeanDeg = 38.0,
            altitudeM = 245.0,
            elevGainM = 840.0,
            fuelL = 1.7,
            headingDeg = 245f,
        ),
        gpsSatCount = 12,
        gpsOnRoad = true,
        weather = WeatherInfo(tempC = 22, humPct = 60, rain = false),
    )

    // ── Routes ────────────────────────────────────────────────────────────────

    private val sampleRouteCards = listOf(
        RouteCardUi(
            id = "r1",
            name = "Tatrzańska pętla",
            dateDisplay = "10 Jul 2026",
            bikeName = "Yamaha MT-07",
            bikeSold = false,
            distanceDisplay = "128.4 km",
            distanceUnitLabel = "km",
            durationDisplay = "2:07:33",
            maxSpeedDisplay = "142 km/h",
            thumbnailPathD = "",
            synced = true,
        ),
        RouteCardUi(
            id = "r2",
            name = "Bieszczady Weekend",
            dateDisplay = "5 Jul 2026",
            bikeName = "Yamaha MT-07",
            bikeSold = false,
            distanceDisplay = "214.0 km",
            distanceUnitLabel = "km",
            durationDisplay = "3:42:10",
            maxSpeedDisplay = "158 km/h",
            thumbnailPathD = "",
            synced = false,
        ),
        RouteCardUi(
            id = "r3",
            name = "Kraków–Zakopane",
            dateDisplay = "28 Jun 2026",
            bikeName = "Yamaha MT-07",
            bikeSold = false,
            distanceDisplay = "96.2 km",
            distanceUnitLabel = "km",
            durationDisplay = "1:28:45",
            maxSpeedDisplay = "131 km/h",
            thumbnailPathD = "",
            synced = true,
        ),
    )

    /** Routes screen with representative route cards. */
    val routesPopulated = RoutesUiState(
        routeCount = 3,
        totalKmDisplay = "438.6 km",
        distanceUnitLabel = "km",
        cards = sampleRouteCards,
    )

    /** Routes screen empty state — no saved routes. */
    val routesEmpty = RoutesUiState()

    // ── Stats ─────────────────────────────────────────────────────────────────

    /** Stats screen with 6-month bar chart and riding-style bars. */
    val statsPopulated = StatsUiState(
        totalDistanceDisplay = "1 234.5 km",
        distanceUnitLabel = "km",
        timeInSaddleDisplay = "18:22",
        ridesCount = 12,
        topSpeedDisplay = "175 km/h",
        speedUnitLabel = "km/h",
        monthBars = listOf(
            MonthBarUi("Feb", "120.0 km", 0.40f),
            MonthBarUi("Mar", "210.5 km", 0.70f),
            MonthBarUi("Apr", "305.0 km", 1.00f),
            MonthBarUi("May", "180.0 km", 0.59f),
            MonthBarUi("Jun", "225.0 km", 0.74f),
            MonthBarUi("Jul", "194.0 km", 0.64f),
        ),
        yearLabel = "2026",
        style = RidingStyleUi(
            avgLeanDisplay = "38°",
            avgLeanFraction = 0.63f,
            avgSpeedDisplay = "48 km/h",
            avgSpeedFraction = 0.48f,
            totalClimbDisplay = "5 920 m",
            totalClimbFraction = 0.74f,
        ),
    )

    /** Stats screen empty state — no rides recorded yet. */
    val statsEmpty = StatsUiState()

    // ── Settings ──────────────────────────────────────────────────────────────

    /** Settings screen with two bikes, broadcast profile, and one pending sync item. */
    val settingsPopulated = SettingsUiState(
        bikes = listOf(
            BikeUi(
                id = "b1",
                name = "Yamaha MT-07",
                yearPlate = "2022 · WA 12345",
                status = BikeStatus.ACTIVE,
                isCurrent = true,
                year = 2022,
                plate = "WA 12345",
            ),
            BikeUi(
                id = "b2",
                name = "Honda CB500F",
                yearPlate = "2019 · KR 98765",
                status = BikeStatus.SOLD,
                isCurrent = false,
                year = 2019,
                plate = "KR 98765",
            ),
        ),
        currentBikeId = "b1",
        theme = "cockpit",
        accent = "#00D1B2",
        language = "pl",
        units = "metric",
        serverAddress = "http://192.168.1.145/gpstrack",
        offline = false,
        autoSync = true,
        pendingRoutes = listOf(
            SyncQueueItemUi(
                routeId = "r2",
                name = "Bieszczady Weekend",
                dateDisplay = "5.07.2026",
                kmDisplay = "214.0 km",
            ),
        ),
        bcName = "Adam Kowalski",
        bcPhone = "+48 601 234 567",
        bcOrigin = "Warszawa",
        bcSocial = "@rider_adam",
        bcBikeDisplay = "Yamaha MT-07 2022",
        bcTodayDisplay = "128.4 km",
        bcTotalDisplay = "1 234.5 km",
        gpsCorrect = true,
        androidAutoEnabled = false,
        autoPause = true,
        keepScreenOn = true,
    )

    // ── Riders ────────────────────────────────────────────────────────────────

    /** Riders screen with group members, live feed, and Bluetooth waves. */
    val ridersPopulated = RidersUiState(
        members = listOf(
            GroupMemberUi(
                initial = "M",
                name = "Marcin Nowak",
                phone = "+48 602 345 678",
                bikeName = "Kawasaki Z900",
            ),
            GroupMemberUi(
                initial = "K",
                name = "Katarzyna Wiśniewska",
                phone = "+48 603 456 789",
                bikeName = "Honda CB650R",
            ),
        ),
        memberCount = 2,
        feedAvailable = true,
        feed = listOf(
            FeedEventUi(
                who = "Marcin",
                value = null,
                isMax = false,
                bikeName = "Kawasaki Z900",
                timeLabel = "10:03",
                dotColor = FeedDotColor.ACCENT,
                type = FeedType.START,
            ),
            FeedEventUi(
                who = "Marcin",
                value = "148 km/h",
                isMax = true,
                bikeName = "Kawasaki Z900",
                timeLabel = "11:24",
                dotColor = FeedDotColor.ACCENT2,
                type = FeedType.MAX,
            ),
            FeedEventUi(
                who = "Katarzyna",
                value = null,
                isMax = false,
                bikeName = "Honda CB650R",
                timeLabel = "12:45",
                dotColor = FeedDotColor.DIM,
                type = FeedType.FINISH,
            ),
        ),
        waves = listOf(
            WaveUi(
                nick = "MX-Rider",
                bikeName = "BMW R1250GS",
                place = "ul. Krakowska, Warszawa",
                timeLabel = "14:32",
            ),
        ),
    )

    // ── Route Detail ──────────────────────────────────────────────────────────

    /** Route detail screen — fully loaded with stats, charts, and weather. */
    val routeDetailPopulated = RouteDetailUiState(
        loading = false,
        routeNotFound = false,
        name = "Tatrzańska pętla",
        dateDisplay = "10 Jul 2026",
        bikeName = "Yamaha MT-07",
        bikeSold = false,
        distanceTile = StatTileUi("128.4", "km"),
        durationTile = StatTileUi("2:07:33", "h:m:s"),
        avgTile = StatTileUi("68", "km/h"),
        maxTile = StatTileUi("142", "km/h"),
        leanTile = StatTileUi("38", "°"),
        fuelTile = StatTileUi("5.1", "L"),
        weather = WeatherUi(
            offline = false,
            tempDisplay = "22°C",
            humDisplay = "60%",
            rain = false,
        ),
        speedStroke = "0,80 80,30 160,60 240,20 320,45",
        speedFill = "0,80 80,30 160,60 240,20 320,45 320,90 0,90",
        elevStroke = "0,60 80,45 160,25 240,35 320,50",
        elevFill = "0,60 80,45 160,25 240,35 320,50 320,90 0,90",
        elevGainLabel = "↑ 840 m",
        thumbnailPathD = "",
        meetings = listOf(
            MeetingUi(
                initials = "MN",
                who = "Marcin Nowak",
                bikeName = "Kawasaki Z900",
                place = "Nowy Targ, ul. Sobieskiego",
                timeLabel = "11:12",
            ),
        ),
        meetingsNone = false,
        queued = false,
        hasCorrectedTrace = true,
        correctionStatus = CorrectionStatus.DONE,
        correctionStatusLabelRes = null,
        confidenceLabel = "94%",
        selectedTrackView = TrackView.RAW,
    )
}
