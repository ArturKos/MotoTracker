package com.mototracker.ui.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker
import kotlinx.coroutines.delay

/**
 * Branded full-screen portrait splash shown while the app is initialising.
 *
 * Renders a stack of nine PNG layers driven by [SplashChoreography] for a smooth
 * entrance animation (AE2/AF1).  Layers are composited back-to-front: mountains, trail,
 * beam, bike_body, wheel_rear, wheel_front, pin, wordmark, tagline.  A frame-delta-clamped
 * manual clock advances elapsed time each vsync — preventing a janky first cold-start frame
 * from skipping ahead in the 2.2 s staged reveal.  Per-phase easing lives entirely inside
 * [SplashChoreography.stateAt].  [onAnimationComplete] is invoked exactly once when elapsed
 * reaches [SplashChoreography.TOTAL_MS].
 *
 * Fallback: if any [R.drawable.splash_l_*] drawable cannot be resolved at composition
 * time, [SplashHero.renderMode] returns [SplashRenderMode.STATIC_FALLBACK] and
 * [R.drawable.splash_portrait_hd] is displayed — composition can never throw due to
 * a missing asset.  The fallback also signals completion after [SplashChoreography.TOTAL_MS]
 * via a timed [LaunchedEffect] so [onAnimationComplete] is always called.
 *
 * A subtle init-status label ([SplashStatus.labelFor]) is overlaid at the bottom.
 * Dismiss timing is governed by [SplashGate] at the call site.
 *
 * @param phase               Current init step; controls the status label via [SplashStatus.labelFor].
 * @param onAnimationComplete Invoked exactly once when the entrance animation genuinely reaches completion.
 * @param modifier            Standard Compose modifier chain.
 */
@Composable
fun SplashScreen(
    phase: SplashPhase,
    onAnimationComplete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val allLayersPresent = remember(context) {
        val layerIds = intArrayOf(
            R.drawable.splash_l_mountains,
            R.drawable.splash_l_trail,
            R.drawable.splash_l_beam,
            R.drawable.splash_l_bike_body,
            R.drawable.splash_l_wheel_rear,
            R.drawable.splash_l_wheel_front,
            R.drawable.splash_l_pin,
            R.drawable.splash_l_wordmark,
            R.drawable.splash_l_tagline,
        )
        layerIds.all { id ->
            try {
                ContextCompat.getDrawable(context, id) != null
            } catch (_: Throwable) {
                false
            }
        }
    }

    val mode = SplashHero.renderMode(allLayersPresent)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101114)),
    ) {
        when (mode) {
            SplashRenderMode.LAYERED -> LayeredHero(
                onAnimationComplete = onAnimationComplete,
                modifier = Modifier.fillMaxSize(),
            )
            SplashRenderMode.STATIC_FALLBACK -> {
                Image(
                    painter = painterResource(R.drawable.splash_portrait_hd),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                LaunchedEffect(Unit) {
                    delay(SplashChoreography.TOTAL_MS)
                    onAnimationComplete()
                }
            }
        }

        Text(
            text = stringResource(SplashStatus.labelFor(phase)),
            color = MotoTracker.colors.dim.copy(alpha = 0.55f),
            style = MotoTracker.typography.label,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}

/**
 * Nine-layer PNG stack animated by [SplashChoreography].
 *
 * A frame-delta-clamped manual clock drives elapsed time: on each vsync [withFrameMillis]
 * supplies the current timestamp in milliseconds; the delta from the previous frame is clamped
 * to 32 ms via [SplashChoreography.clampDelta] before being added to the accumulator.  This
 * prevents a large busy-frame delta on cold-start from skipping ahead in the 2.2 s staged
 * reveal.  [onAnimationComplete] is invoked exactly once after the accumulator reaches
 * [SplashChoreography.TOTAL_MS] and the loop exits.
 *
 * @param onAnimationComplete Invoked once when elapsed reaches [SplashChoreography.TOTAL_MS].
 * @param modifier            Standard Compose modifier chain.
 */
@Composable
private fun LayeredHero(
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (elapsed < SplashChoreography.TOTAL_MS) {
            withFrameMillis { now ->
                elapsed = (elapsed + SplashChoreography.clampDelta(last, now))
                    .coerceAtMost(SplashChoreography.TOTAL_MS)
                last = now
            }
        }
        onAnimationComplete()
    }
    val layers = SplashChoreography.stateAt(elapsed)

    Box(modifier = modifier) {
        // Back → Front z-order
        SplashLayer(R.drawable.splash_l_mountains, layers.mountains)
        SplashLayer(R.drawable.splash_l_trail, layers.trail)
        SplashLayer(R.drawable.splash_l_beam, layers.bike)         // beam rides with bike
        SplashLayer(R.drawable.splash_l_bike_body, layers.bike)
        SplashLayer(
            drawableRes = R.drawable.splash_l_wheel_rear,
            state = layers.wheelRear,
            transformOrigin = TransformOrigin(0.1948f, 0.3936f),
        )
        SplashLayer(
            drawableRes = R.drawable.splash_l_wheel_front,
            state = layers.wheelFront,
            transformOrigin = TransformOrigin(0.3665f, 0.3965f),
        )
        SplashLayer(R.drawable.splash_l_pin, layers.pin)
        SplashLayer(R.drawable.splash_l_wordmark, layers.wordmark)
        SplashLayer(R.drawable.splash_l_tagline, layers.wordmark)  // tagline rides with wordmark
    }
}

/**
 * Single splash layer: a full-size [Image] with a [graphicsLayer] driven by [state].
 *
 * [translationX] is derived from [LayerState.translateXFrac] multiplied by the layer's
 * measured pixel width inside [graphicsLayer], so horizontal offsets scale correctly
 * across all screen densities.
 *
 * @param drawableRes     Drawable resource id for this layer's PNG asset.
 * @param state           Transform snapshot from [SplashChoreography.stateAt].
 * @param transformOrigin Pivot point for rotation and scale; defaults to [TransformOrigin.Center].
 */
@Composable
private fun SplashLayer(
    drawableRes: Int,
    state: LayerState,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = state.alpha
                translationX = state.translateXFrac * size.width
                translationY = state.translateYPx
                rotationZ = state.rotationDeg
                scaleX = state.scale
                scaleY = state.scale
                this.transformOrigin = transformOrigin
            },
    )
}
