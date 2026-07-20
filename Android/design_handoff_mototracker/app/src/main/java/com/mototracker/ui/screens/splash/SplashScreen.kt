package com.mototracker.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

/**
 * Branded full-screen portrait splash shown while the app is initialising.
 *
 * Renders a stack of nine PNG layers driven by [SplashChoreography] for a smooth
 * entrance animation (AE2).  Layers are composited back-to-front: mountains, trail,
 * beam, bike_body, wheel_rear, wheel_front, pin, wordmark, tagline.  A single
 * [Animatable] progresses linearly over [SplashChoreography.TOTAL_MS] ms; per-phase
 * easing lives entirely inside [SplashChoreography.stateAt].
 *
 * Fallback: if any [R.drawable.splash_l_*] drawable cannot be resolved at composition
 * time, [SplashHero.renderMode] returns [SplashRenderMode.STATIC_FALLBACK] and
 * [R.drawable.splash_portrait_hd] is displayed — composition can never throw due to
 * a missing asset.
 *
 * A subtle init-status label ([SplashStatus.labelFor]) is overlaid at the bottom.
 * Dismiss timing is governed by [SplashGate] at the call site.
 *
 * @param phase    Current init step; controls the status label via [SplashStatus.labelFor].
 * @param modifier Standard Compose modifier chain.
 */
@Composable
fun SplashScreen(
    phase: SplashPhase,
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
            SplashRenderMode.LAYERED -> LayeredHero(modifier = Modifier.fillMaxSize())
            SplashRenderMode.STATIC_FALLBACK -> Image(
                painter = painterResource(R.drawable.splash_portrait_hd),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
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
 * A single [Animatable] drives elapsed time (ms) from 0 to [SplashChoreography.TOTAL_MS]
 * at a constant wall-clock rate; each frame [SplashChoreography.stateAt] derives the
 * [SplashLayers] snapshot applied to each layer's [graphicsLayer].  Per-phase easing
 * lives inside [SplashChoreography] — the Animatable itself is always linear.
 */
@Composable
private fun LayeredHero(modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(
            targetValue = SplashChoreography.TOTAL_MS.toFloat(),
            animationSpec = tween(
                durationMillis = SplashChoreography.TOTAL_MS.toInt(),
                easing = LinearEasing,
            ),
        )
    }
    val layers = SplashChoreography.stateAt(anim.value.toLong())

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
