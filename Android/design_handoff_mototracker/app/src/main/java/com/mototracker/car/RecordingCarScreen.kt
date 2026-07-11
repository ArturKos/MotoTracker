package com.mototracker.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.mototracker.R
import com.mototracker.ui.screens.record.RecordingPhase
import com.mototracker.domain.recording.RecordingMetrics
import com.mototracker.ui.state.Units
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Android Auto glanceable recording screen (🔬 — rendered by the head-unit host, not verifiable headless).
 *
 * Observes [CarRecordingBridge] for live recording state and renders a [PaneTemplate]
 * with three rows (speed · time/distance · lean/altitude) and up to two action buttons.
 * Calls [invalidate] whenever state changes so the host re-fetches the template.
 *
 * @param carContext Car context provided by [RecordingSession].
 * @param bridge     App-scoped bridge supplying live recording state and accepting car actions.
 */
class RecordingCarScreen(
    carContext: CarContext,
    private val bridge: CarRecordingBridge,
) : Screen(carContext) {

    @Volatile
    private var currentState: CarDashboardState = CarRecordingUiMapper.map(
        RecordingMetrics(),
        RecordingPhase.Idle,
        Units.METRIC,
    )

    init {
        lifecycleScope.launch {
            combine(bridge.metrics, bridge.phase, bridge.units) { metrics, phase, units ->
                CarRecordingUiMapper.map(metrics, phase, units)
            }.collect { newState ->
                currentState = newState
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val s = currentState
        val ctx = carContext

        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("${s.speedText} ${s.speedUnit}")
                    .addText(ctx.getString(R.string.tile_speed))
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(s.timeText)
                    .addText("${s.distanceText} ${s.distanceUnit}")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(s.leanText)
                    .addText("${s.altitudeText} ${s.altitudeUnit}")
                    .build()
            )

        s.actions.forEach { action ->
            val label: String
            val onClick: () -> Unit
            when (action) {
                CarAction.Start -> {
                    label = ctx.getString(R.string.btn_start_ride)
                    onClick = bridge::start
                }
                CarAction.Pause -> {
                    label = ctx.getString(R.string.btn_pause)
                    onClick = bridge::pause
                }
                CarAction.Resume -> {
                    label = ctx.getString(R.string.btn_resume)
                    onClick = bridge::resume
                }
                CarAction.Stop -> {
                    label = ctx.getString(R.string.btn_finish)
                    onClick = bridge::stop
                }
            }
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle(label)
                    .setOnClickListener { onClick() }
                    .build()
            )
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeaderAction(Action.BACK)
            .setTitle(ctx.getString(R.string.screen_record))
            .build()
    }
}
