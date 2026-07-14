package com.mototracker.ui.screens.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mototracker.R
import com.mototracker.domain.recording.LeanBarGeometry
import com.mototracker.ui.theme.MotoTracker
import java.util.Locale
import kotlin.math.abs

/** Full-scale degrees for the tilt bar (±45° maps to ±100% of the bar). */
private const val LEAN_BAR_SCALE_DEG = 45.0

/**
 * Horizontal tilt bar showing live lean angle fill plus ghost markers for the session
 * max-left and max-right lean angles.
 *
 * - The bar is centered at 0°.
 * - Current lean fills toward the left (negative degrees) or right (positive degrees).
 * - Ghost markers (thin vertical ticks) mark the session max reached on each side.
 * - The current signed lean value in degrees is shown beneath the bar.
 * - 'L' / 'R' labels are rendered at the bar edges.
 *
 * This composable is a pure renderer — all geometry math is delegated to [LeanBarGeometry]
 * so the math can be unit-tested independently.
 *
 * @param currentLeanDeg  Current signed lean angle in degrees (+right, -left).
 * @param maxLeanLeftDeg  Non-negative magnitude of the session peak leftward lean.
 * @param maxLeanRightDeg Non-negative magnitude of the session peak rightward lean.
 * @param modifier        Standard Compose modifier.
 */
@Composable
fun LeanTiltBar(
    currentLeanDeg: Double,
    maxLeanLeftDeg: Double,
    maxLeanRightDeg: Double,
    modifier: Modifier = Modifier,
) {
    val accent = MotoTracker.colors.accent
    val accent2 = MotoTracker.colors.accent2
    val panel2 = MotoTracker.colors.panel2
    val dimColor = MotoTracker.colors.dim

    val fillFraction = LeanBarGeometry.fillFraction(currentLeanDeg, LEAN_BAR_SCALE_DEG).toFloat()
    val (leftMarkerFrac, rightMarkerFrac) = LeanBarGeometry.markerFractions(
        leftMagnitudeDeg = maxLeanLeftDeg,
        rightMagnitudeDeg = maxLeanRightDeg,
        maxScaleDeg = LEAN_BAR_SCALE_DEG,
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MotoTracker.colors.panel,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.label_lean_tilt),
                style = MotoTracker.typography.label,
                color = dimColor,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.label_lean_left_marker),
                    style = MotoTracker.typography.label,
                    color = dimColor,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp),
                    ) {
                        val barH = size.height
                        val barW = size.width
                        val centerX = barW / 2f

                        // Background track
                        drawRect(
                            color = panel2,
                            topLeft = Offset(0f, barH * 0.3f),
                            size = Size(barW, barH * 0.4f),
                        )

                        // Current lean fill (from center toward L or R)
                        val fillEndX = centerX + fillFraction * centerX
                        val fillLeft = if (fillEndX < centerX) fillEndX else centerX
                        val fillWidth = if (fillEndX < centerX) centerX - fillEndX else fillEndX - centerX
                        if (fillWidth > 0f) {
                            drawRect(
                                color = accent,
                                topLeft = Offset(fillLeft, barH * 0.2f),
                                size = Size(fillWidth, barH * 0.6f),
                            )
                        }

                        // Center line
                        drawLine(
                            color = dimColor,
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, barH),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round,
                        )

                        // Left ghost marker (accent2 = warning colour)
                        if (maxLeanLeftDeg > 0.0) {
                            val lx = (leftMarkerFrac * barW).toFloat()
                            drawLine(
                                color = accent2,
                                start = Offset(lx, 0f),
                                end = Offset(lx, barH),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }

                        // Right ghost marker
                        if (maxLeanRightDeg > 0.0) {
                            val rx = (rightMarkerFrac * barW).toFloat()
                            drawLine(
                                color = accent2,
                                start = Offset(rx, 0f),
                                end = Offset(rx, barH),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.label_lean_right_marker),
                    style = MotoTracker.typography.label,
                    color = dimColor,
                )
            }

            // Current angle and session max values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = String.format(
                        Locale.US, "%s %.1f°",
                        stringResource(R.string.label_lean_max_left),
                        maxLeanLeftDeg,
                    ),
                    style = MotoTracker.typography.bodySmall,
                    color = dimColor,
                )
                Text(
                    text = String.format(Locale.US, "%.1f°", abs(currentLeanDeg)),
                    style = MotoTracker.typography.bigCardNumber,
                    color = MotoTracker.colors.text,
                )
                Text(
                    text = String.format(
                        Locale.US, "%.1f° %s",
                        maxLeanRightDeg,
                        stringResource(R.string.label_lean_max_right),
                    ),
                    style = MotoTracker.typography.bodySmall,
                    color = dimColor,
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

/**
 * Frozen variant of [LeanTiltBar] for route-detail screens.
 *
 * Shows only the max-left and max-right markers (no live fill — current lean is undefined
 * for a saved route). The bar fills to the larger of the two maxima to give a visual sense
 * of the ride's lean envelope.
 *
 * @param maxLeanLeftDeg  Non-negative magnitude of the peak leftward lean on this route.
 * @param maxLeanRightDeg Non-negative magnitude of the peak rightward lean on this route.
 * @param modifier        Standard Compose modifier.
 */
@Composable
fun FrozenLeanTiltBar(
    maxLeanLeftDeg: Double,
    maxLeanRightDeg: Double,
    modifier: Modifier = Modifier,
) {
    LeanTiltBar(
        currentLeanDeg = 0.0,
        maxLeanLeftDeg = maxLeanLeftDeg,
        maxLeanRightDeg = maxLeanRightDeg,
        modifier = modifier,
    )
}
