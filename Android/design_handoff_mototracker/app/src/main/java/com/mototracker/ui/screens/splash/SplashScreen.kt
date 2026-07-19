package com.mototracker.ui.screens.splash

import android.graphics.drawable.AnimatedVectorDrawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker

/**
 * Branded full-screen splash shown while the app is initialising.
 *
 * Hosts the hero banner via an [ImageView]-wrapped [AnimatedVectorDrawable]
 * ([R.drawable.splash_hero_avd]).  Using [AndroidView] avoids the experimental
 * [androidx.compose.animation.graphics] API that caused intermittent
 * `finishTopCrashedActivity` crashes on Compose-AVD composition.
 *
 * Fallback: if the AVD drawable fails to load or cast to [AnimatedVectorDrawable]
 * for any reason, [SplashHero.renderMode] returns [SplashRenderMode.STATIC_FALLBACK]
 * and a plain raster ([R.drawable.splash_hero_hd]) is shown instead — composition
 * can never throw.
 *
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MotoTracker.colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HeroSlot(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1536f / 440f),
            )

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = stringResource(SplashStatus.labelFor(phase)),
                color = MotoTracker.colors.dim.copy(alpha = 0.55f),
                style = MotoTracker.typography.label,
            )
        }
    }
}

/**
 * Renders the 1536×440 hero banner.
 *
 * The render path is decided once per composition by pre-checking whether
 * [R.drawable.splash_hero_avd] is an [AnimatedVectorDrawable]; the result is
 * passed to the pure [SplashHero.renderMode] helper which returns either
 * [SplashRenderMode.ANIMATED] or [SplashRenderMode.STATIC_FALLBACK].
 *
 * When [SplashRenderMode.ANIMATED]: an [AndroidView] hosts an [ImageView] that
 * loads and starts the AVD once (in the factory; never restarted on recomposition).
 * A try/catch around the start() call ensures any late failure is silent.
 *
 * When [SplashRenderMode.STATIC_FALLBACK]: a plain raster [R.drawable.splash_hero_hd]
 * is shown so the splash is always visible regardless of drawable availability.
 */
@Composable
private fun HeroSlot(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Determine AVD availability before entering the AndroidView factory so we
    // never mutate state during composition. This check runs once per context
    // identity (i.e. once per Activity lifetime).
    val avdLoadSucceeded = remember(context) {
        try {
            ContextCompat.getDrawable(context, R.drawable.splash_hero_avd) is AnimatedVectorDrawable
        } catch (_: Throwable) {
            false
        }
    }

    val mode = SplashHero.renderMode(avdLoadSucceeded)

    when (mode) {
        SplashRenderMode.ANIMATED -> {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        try {
                            setImageResource(R.drawable.splash_hero_avd)
                            (drawable as? AnimatedVectorDrawable)?.start()
                        } catch (_: Throwable) {
                            // start() failed after pre-check passed; the AVD is already
                            // loaded so the first frame is still shown — acceptable.
                        }
                    }
                },
                modifier = modifier,
            )
        }

        SplashRenderMode.STATIC_FALLBACK -> {
            Image(
                painter = painterResource(R.drawable.splash_hero_hd),
                contentDescription = null,
                modifier = modifier,
            )
        }
    }
}
