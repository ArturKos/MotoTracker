package com.mototracker.car

import com.mototracker.domain.recording.RecordingMetrics
import com.mototracker.ui.screens.record.RecordingEvent
import com.mototracker.ui.screens.record.RecordingPhase
import com.mototracker.ui.state.Units
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped singleton that bridges the phone-side [com.mototracker.ui.screens.record.RecordingViewModel]
 * to the Android Auto [RecordingCarScreen].
 *
 * State flows one way: phone → car (via [publish] / [publishUnits]).
 * Control commands flow the opposite way: car → phone (via [commands]).
 *
 * Keeping this bridge thin ensures the Car App Service (which has its own lifecycle)
 * can always observe the latest recording state without coupling to the phone ViewModel.
 */
@Singleton
class CarRecordingBridge @Inject constructor() {

    private val _metrics = MutableStateFlow(RecordingMetrics())

    /** Latest recording metrics, updated by [com.mototracker.ui.screens.record.RecordingViewModel]. */
    val metrics: StateFlow<RecordingMetrics> = _metrics.asStateFlow()

    private val _phase = MutableStateFlow(RecordingPhase.Idle)

    /** Current recording phase, updated by [com.mototracker.ui.screens.record.RecordingViewModel]. */
    val phase: StateFlow<RecordingPhase> = _phase.asStateFlow()

    private val _units = MutableStateFlow(Units.METRIC)

    /** Active measurement-system preference, mirrored from app settings. */
    val units: StateFlow<Units> = _units.asStateFlow()

    private val _commands = MutableSharedFlow<RecordingEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Commands emitted by the car screen — collected by
     * [com.mototracker.ui.screens.record.RecordingViewModel] to drive its state machine.
     */
    val commands: SharedFlow<RecordingEvent> = _commands.asSharedFlow()

    // ── Phone → Car ──────────────────────────────────────────────────────────

    /** Publishes updated metrics and phase from the phone recording session. */
    fun publish(metrics: RecordingMetrics, phase: RecordingPhase) {
        _metrics.value = metrics
        _phase.value = phase
    }

    /** Updates the unit preference shown on the car screen. */
    fun publishUnits(units: Units) {
        _units.value = units
    }

    // ── Car → Phone ──────────────────────────────────────────────────────────

    /** Requests the phone to start a new recording session. */
    fun start() { _commands.tryEmit(RecordingEvent.Start) }

    /** Requests the phone to pause the current recording session. */
    fun pause() { _commands.tryEmit(RecordingEvent.Pause) }

    /** Requests the phone to resume the paused recording session. */
    fun resume() { _commands.tryEmit(RecordingEvent.Resume) }

    /** Requests the phone to finish and save the current recording session. */
    fun stop() { _commands.tryEmit(RecordingEvent.Finish) }
}
