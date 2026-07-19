package com.mototracker.ui.screens.splash

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker

/**
 * Branded full-screen splash shown while the app is initialising.
 *
 * Hosts [splash_hero_avd] — a ~2.1 s AnimatedVectorDrawable that animates the
 * 1536×440 hero banner through four overlapping phases (bike rolls in, trail
 * reveals, pin drops, wordmark fades in). The AVD plays a single forward pass
 * (atEnd false → true); dismiss timing is governed by [SplashGate] at the
 * call site, which waits for [SplashGate.AVD_DURATION_MS] before dismissing.
 *
 * This composable is purely presentational — no side effects, no ViewModel.
 *
 * @param phase    Current init step; controls the status label via [SplashStatus.labelFor].
 * @param modifier Standard Compose modifier chain.
 */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun SplashScreen(
    phase: SplashPhase,
    modifier: Modifier = Modifier,
) {
    val image = AnimatedImageVector.animatedVectorResource(R.drawable.splash_hero_avd)
    var atEnd by remember { mutableStateOf(false) }

    // Single forward pass: trigger the AVD start→end play once on composition entry.
    LaunchedEffect(Unit) {
        atEnd = true
    }

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
            // The hero VD is a 1536×440 wide banner — fill available width at the
            // correct aspect ratio so it is never distorted or clipped.
            Image(
                painter = rememberAnimatedVectorPainter(animatedImageVector = image, atEnd = atEnd),
                contentDescription = null,
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
