package com.mototracker.ui.screens.splash

import android.graphics.drawable.AnimatedVectorDrawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
 * Branded full-screen portrait splash shown while the app is initialising (AD1).
 *
 * The hero fills the screen via [HeroSlot] ([R.drawable.splash_portrait_avd]).
 * [AndroidView] hosts an [ImageView] instead of Compose's experimental AVD API —
 * this avoids the `finishTopCrashedActivity` crashes seen on Android 9 (AB1 fix).
 *
 * Fallback: if the AVD drawable fails to cast to [AnimatedVectorDrawable],
 * [SplashHero.renderMode] returns [SplashRenderMode.STATIC_FALLBACK] and
 * [R.drawable.splash_portrait_hd] is shown — composition never throws.
 *
 * A subtle init-status label ([SplashStatus.labelFor]) is overlaid at the bottom.
 * The wordmark is embedded inside the portrait AVD itself so no separate Text row
 * is needed for branding.
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
    ) {
        HeroSlot(modifier = Modifier.fillMaxSize())

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
 * Renders the 1080×1560 portrait hero frame.
 *
 * Render path is decided once per composition by pre-checking whether
 * [R.drawable.splash_portrait_avd] loads as an [AnimatedVectorDrawable]; the
 * boolean is passed to the pure [SplashHero.renderMode] seam.
 *
 * [SplashRenderMode.ANIMATED]: [AndroidView] hosts an [ImageView] that loads and
 * starts the AVD once in the factory; a try/catch around [AnimatedVectorDrawable.start]
 * ensures any late drawable failure is silently swallowed — the first static frame
 * is still displayed and composition cannot throw.
 *
 * [SplashRenderMode.STATIC_FALLBACK]: [R.drawable.splash_portrait_hd] is shown so
 * the splash is always visible regardless of drawable availability.
 */
@Composable
private fun HeroSlot(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val avdLoadSucceeded = remember(context) {
        try {
            ContextCompat.getDrawable(context, R.drawable.splash_portrait_avd) is AnimatedVectorDrawable
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
                            setImageResource(R.drawable.splash_portrait_avd)
                            (drawable as? AnimatedVectorDrawable)?.start()
                        } catch (_: Throwable) {
                            // start() failed after pre-check passed — first frame still shown.
                        }
                    }
                },
                modifier = modifier,
            )
        }

        SplashRenderMode.STATIC_FALLBACK -> {
            Image(
                painter = painterResource(R.drawable.splash_portrait_hd),
                contentDescription = null,
                modifier = modifier,
            )
        }
    }
}
