package com.mototracker.ui.screens.record

import com.mototracker.core.format.CoordFormat
import com.mototracker.data.recording.ActiveSessionSnapshot
import com.mototracker.domain.fuel.FuelAdjustmentMode
import com.mototracker.domain.recording.RecordingMetrics
import com.mototracker.domain.recording.TrackPoint

/** Recording session lifecycle phase. */
enum class RecordingPhase { Idle, Recording, Paused }

/**
 * Weather conditions captured at recording time.
 *
 * @param tempC  Temperature in Celsius.
 * @param humPct Relative humidity 0–100.
 * @param rain   Whether precipitation is occurring.
 */
data class WeatherInfo(val tempC: Int, val humPct: Int, val rain: Boolean)

/**
 * Full UI state for the Recording screen.
 *
 * Drives all Compose rendering without any Android context in the data layer.
 *
 * @param phase             Current recording lifecycle phase.
 * @param metrics           Live cumulative metrics from [com.mototracker.domain.recording.RecordingEngine].
 * @param gpsSatCount       Number of GPS satellites in use; shown in the GPS chip.
 * @param gpsOnRoad         Whether GPS-to-road correction is active (from settings).
 * @param weather           Current weather, or null when offline / not yet fetched.
 * @param trackPoints       Ordered GPS track points accumulated during the active recording session;
 *                          reset to empty on Start and on Finish.
 * @param keepScreenOn      Whether the display should stay on during the ride (from settings).
 *                          Applied to the window via FLAG_KEEP_SCREEN_ON while phase is
 *                          Recording or Paused; cleared on Idle and on screen disposal.
 * @param resumableSession  A previously interrupted session snapshot detected on startup (B20).
 *                          Non-null triggers the resume-or-discard prompt. Null once the user
 *                          has acted on it or when no unfinished session exists.
 * @param activeRouteId     UUID pre-assigned to the active route at recording start.
 *                          Passed to [com.mototracker.service.RecordingService] via Intent
 *                          so BLE-discovered waves are associated with the correct route.
 *                          Null when not recording.
 * @param liveHeadingDeg    Tilt-compensated magnetic azimuth in degrees [0, 360) from the
 *                          magnetometer, populated whenever the screen is active regardless of
 *                          recording phase.  Null until the first sensor reading arrives.
 *                          Used to animate the compass in Idle/Paused before GPS bearing is
 *                          available (F2).
 * @param liveLeanDeg       Live lean angle in degrees from the gravity sensor, populated
 *                          regardless of recording phase.  Null until the first reading.
 *                          Used to animate the lean bar in Idle so the rider can orient the
 *                          phone before starting (F2).
 * @param liveSpeedKmh      Live GPS speed in km/h, populated regardless of recording phase
 *                          from the raw GPS sample so the Idle cockpit shows a real-time
 *                          readout before the ride starts (M2).  Null until the first fix.
 * @param liveAltitudeM     Live GPS altitude in metres above sea level, populated regardless
 *                          of recording phase from the raw GPS sample so the Idle cockpit
 *                          shows a real-time readout before the ride starts (M2).
 *                          Null until the first fix.
 * @param liveLat           Live GPS latitude in decimal degrees (WGS-84), populated regardless of
 *                          recording phase from the raw GPS sample so the top GPS-chip row shows a
 *                          real-time coordinate readout (P1). Null until the first fix.
 * @param liveLng           Live GPS longitude in decimal degrees (WGS-84), populated regardless of
 *                          recording phase from the raw GPS sample so the top GPS-chip row shows a
 *                          real-time coordinate readout (P1). Null until the first fix.
 * @param coordFormat       Coordinate display format (DD / DMS / UTM) sourced from the user's
 *                          settings and mapped via [CoordFormat.fromKey] (P2). Applied to
 *                          [liveLat] / [liveLng] in the GPS chip row.
 * @param fuelPricePerL        Fuel price per litre from the current bike's configuration; null when
 *                             the bike has no price set.  Used to compute the running fuel cost
 *                             displayed in the fuel readout (G2).
 * @param currency             ISO 4217 currency code for fuel cost display (e.g. "PLN", "EUR").
 *                             Sourced from [com.mototracker.data.settings.AppSettings.currency].
 * @param showRefuelDialog     `true` when the refuel-input dialog should be visible (G5).
 * @param refuelDialogLitres   Pre-filled litres value for the refuel dialog (bike tank capacity or 0).
 * @param refuelDialogPricePerL Pre-filled price/L for the refuel dialog (bike default or null).
 * @param showStopConfirmDialog `true` when the stop-confirmation dialog should be visible (J4).
 *                              Recording continues untouched while the dialog is open; only
 *                              [RecordingEvent.ConfirmStop] leads to an actual finish and save.
 * @param showBatteryOptPrompt  `true` when the battery-optimization exemption dialog should be
 *                              shown (O1). Set before recording starts when the app is not exempt
 *                              and the user has not yet dismissed the prompt.
 * @param showFuelCorrectionDialog `true` when the fuel-correction dialog should be visible (R1).
 * @param fuelCorrectionCurrentRemaining Pre-computed current remaining fuel (litres) shown as context
 *                                       in the fuel-correction dialog (R1).
 */
data class RecordingUiState(
    val phase: RecordingPhase = RecordingPhase.Idle,
    val metrics: RecordingMetrics = RecordingMetrics(),
    val gpsSatCount: Int = 0,
    val gpsOnRoad: Boolean = false,
    val keepScreenOn: Boolean = false,
    val weather: WeatherInfo? = null,
    val trackPoints: List<TrackPoint> = emptyList(),
    val resumableSession: ActiveSessionSnapshot? = null,
    val activeRouteId: String? = null,
    val liveHeadingDeg: Float? = null,
    val liveLeanDeg: Double? = null,
    val liveSpeedKmh: Double? = null,
    val liveAltitudeM: Double? = null,
    val liveLat: Double? = null,
    val liveLng: Double? = null,
    val coordFormat: CoordFormat = CoordFormat.DECIMAL_DEGREES,
    val fuelPricePerL: Double? = null,
    val currency: String = "PLN",
    val showRefuelDialog: Boolean = false,
    val refuelDialogLitres: Double = 0.0,
    val refuelDialogPricePerL: Double? = null,
    val showStopConfirmDialog: Boolean = false,
    val showBatteryOptPrompt: Boolean = false,
    val showFuelCorrectionDialog: Boolean = false,
    val fuelCorrectionCurrentRemaining: Double = 0.0,
)

/** One-shot events dispatched from the Recording screen to the ViewModel. */
sealed class RecordingEvent {
    /** User tapped "Start ride". */
    data object Start : RecordingEvent()
    /** User tapped "Pause". */
    data object Pause : RecordingEvent()
    /** User tapped "Resume". */
    data object Resume : RecordingEvent()
    /** User tapped "Finish". */
    data object Finish : RecordingEvent()
    /** User chose to resume an interrupted session detected on startup (B20). */
    data object ResumeSession : RecordingEvent()
    /** User chose to discard an interrupted session detected on startup (B20). */
    data object DiscardSession : RecordingEvent()
    /**
     * User tapped the fuel-pump quick action; opens the refuel input dialog (G5).
     *
     * Supersedes the old instant fill-to-full — the dialog lets the user confirm
     * litres and price before the event is recorded. Ignored when the current bike
     * has no tank capacity configured (same guard as E4).
     */
    data object ShowRefuelDialog : RecordingEvent()

    /**
     * User confirmed a refuel event from the dialog (G5).
     *
     * @param litres    Volume of fuel added in litres.
     * @param pricePerL Price per litre at the time of the event.
     */
    data class ConfirmRefuel(val litres: Double, val pricePerL: Double) : RecordingEvent()

    /** User dismissed the refuel dialog without confirming (G5). */
    data object DismissRefuelDialog : RecordingEvent()

    /**
     * User tapped the Stop button — shows the stop-confirmation dialog (J4).
     *
     * Recording continues untouched; no save or phase change occurs until [ConfirmStop].
     */
    data object RequestStop : RecordingEvent()

    /**
     * User confirmed the stop dialog — finishes the ride and saves the route (J4).
     *
     * Internally delegates to the existing `doFinish()` path.
     */
    data object ConfirmStop : RecordingEvent()

    /**
     * User dismissed the stop-confirmation dialog — recording continues unchanged (J4).
     */
    data object DismissStopDialog : RecordingEvent()

    /**
     * Requests continuation of an existing saved route (J5).
     *
     * Dispatched either by the route-detail bus listener in [RecordingViewModel.init]
     * or directly via [RecordingViewModel.onEvent] for testing purposes.
     * Ignored unless the current phase is [RecordingPhase.Idle] with no pending
     * resumable session.
     *
     * @param routeId UUID of the saved route to continue.
     */
    data class ResumeRoute(val routeId: String) : RecordingEvent()

    /**
     * User tapped "Allow" in the battery-optimization prompt (O1).
     *
     * Clears the prompt; the Compose layer fires the exemption intent.
     */
    data object BatteryOptConfirm : RecordingEvent()

    /**
     * User tapped "Not now" in the battery-optimization prompt (O1).
     *
     * Persists the dismissed flag and clears the prompt so it is not shown again.
     */
    data object BatteryOptDismiss : RecordingEvent()

    /**
     * User tapped the fuel-correction affordance during an active or paused ride (R1).
     *
     * Opens the fuel-correction dialog pre-filled with the current remaining fuel.
     */
    data object ShowFuelCorrectionDialog : RecordingEvent()

    /**
     * User confirmed a fuel-level correction from the dialog (R1).
     *
     * @param mode  Whether the correction sets an absolute level or applies a delta.
     * @param value Correction value in litres (absolute or signed).
     */
    data class ConfirmFuelCorrection(val mode: FuelAdjustmentMode, val value: Double) : RecordingEvent()

    /** User dismissed the fuel-correction dialog without confirming (R1). */
    data object DismissFuelCorrectionDialog : RecordingEvent()
}

/** One-shot side-effects emitted by the ViewModel to the UI layer. */
sealed class RecordingEffect {
    /**
     * The route was saved successfully.
     *
     * @param offline `true` when the route was saved locally (no server upload was attempted).
     */
    data class Saved(val offline: Boolean) : RecordingEffect()

    /**
     * Navigate to the detail screen for the just-saved route.
     * Wired to actual navigation in B4; the screen may no-op until then.
     *
     * @param routeId UUID of the saved route.
     */
    data class NavigateToDetail(val routeId: String) : RecordingEffect()
}
