package com.mototracker.ui.screens.splash

import android.graphics.BitmapFactory
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Branded full-screen portrait splash shown while the app is initialising.
 *
 * Renders a stack of nine PNG layers driven by [SplashChoreography] for a smooth
 * entrance animation (AE2/AF1/AF2).  Layer IDs are sourced from [SplashAssets.layerDrawableRes]
 * (back-to-front: mountains, trail, beam, bike_body, wheel_rear, wheel_front, pin, wordmark,
 * tagline).  A frame-delta-clamped manual clock advances elapsed time each vsync — preventing
 * a janky first cold-start frame from skipping ahead in the 2.2 s staged reveal.  Per-phase
 * easing lives entirely inside [SplashChoreography.stateAt].  [onAnimationComplete] is invoked
 * exactly once when elapsed reaches [SplashChoreography.TOTAL_MS].
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
        SplashAssets.layerDrawableRes.all { id ->
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
 * **Bitmap warm-up (AF2):** before the animation clock starts all nine [SplashAssets.layerDrawableRes]
 * PNGs (1080×1560) are decoded to [ImageBitmap] on [Dispatchers.IO] inside a [LaunchedEffect].
 * During the decode the static mountains layer (matching the system window background) is shown
 * so there is no visual jump.  The frame-delta-clamped clock starts only after [warmed] flips to
 * `true`, guaranteeing the first animated frame is never competing with bitmap decode.
 *
 * Once warmed, each layer is rendered via [SplashLayerFromBitmap] from the cached [ImageBitmap],
 * keeping identical [graphicsLayer] transforms, [ContentScale.Fit], and custom [TransformOrigin]
 * values for the wheel layers.
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
    val context = LocalContext.current
    var warmed by remember { mutableStateOf(false) }
    var cachedBitmaps by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        // Decode all nine layer PNGs on IO before the animation clock starts (AF2).
        val bitmaps = withContext(Dispatchers.IO) {
            SplashAssets.layerDrawableRes.map { id ->
                BitmapFactory.decodeResource(context.resources, id).asImageBitmap()
            }
        }
        cachedBitmaps = bitmaps
        warmed = true

        // Animation clock starts only after bitmaps are ready.
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
        if (!warmed) {
            // Static mountains hold while bitmaps are decoded — matches the windowBackground
            // so there is no visual jump from the system splash to the first Compose frame.
            SplashLayer(SplashAssets.layerDrawableRes[0], LayerState())
        } else {
            // Back → Front z-order; indices match SplashAssets.layerDrawableRes order.
            SplashLayerFromBitmap(cachedBitmaps[0], layers.mountains)
            SplashLayerFromBitmap(cachedBitmaps[1], layers.trail)
            SplashLayerFromBitmap(cachedBitmaps[2], layers.bike)        // beam rides with bike
            SplashLayerFromBitmap(cachedBitmaps[3], layers.bike)        // bike_body
            SplashLayerFromBitmap(
                bitmap = cachedBitmaps[4],
                state = layers.wheelRear,
                transformOrigin = TransformOrigin(0.1948f, 0.3936f),
            )
            SplashLayerFromBitmap(
                bitmap = cachedBitmaps[5],
                state = layers.wheelFront,
                transformOrigin = TransformOrigin(0.3665f, 0.3965f),
            )
            SplashLayerFromBitmap(cachedBitmaps[6], layers.pin)
            SplashLayerFromBitmap(cachedBitmaps[7], layers.wordmark)
            SplashLayerFromBitmap(cachedBitmaps[8], layers.wordmark)   // tagline rides with wordmark
        }
    }
}

/**
 * Single splash layer rendered from a pre-decoded [ImageBitmap] with a [graphicsLayer] driven
 * by [state] (AF2 warmed path).
 *
 * [translationX] is derived from [LayerState.translateXFrac] multiplied by the layer's
 * measured pixel width inside [graphicsLayer], so horizontal offsets scale correctly
 * across all screen densities.
 *
 * @param bitmap          Pre-decoded bitmap for this layer's PNG asset.
 * @param state           Transform snapshot from [SplashChoreography.stateAt].
 * @param transformOrigin Pivot point for rotation and scale; defaults to [TransformOrigin.Center].
 */
@Composable
private fun SplashLayerFromBitmap(
    bitmap: ImageBitmap,
    state: LayerState,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
) {
    Image(
        bitmap = bitmap,
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

/**
 * Single splash layer: a full-size [Image] with a [graphicsLayer] driven by [state].
 *
 * Used for the static pre-warm mountains display while [LayeredHero] decodes bitmaps on IO.
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
