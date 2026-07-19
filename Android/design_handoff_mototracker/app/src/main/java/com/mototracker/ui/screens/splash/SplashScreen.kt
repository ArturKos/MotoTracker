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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.delay

/**
 * Branded full-screen splash shown while the app is initialising.
 *
 * Renders the animated motorcycle vector mark centred on the themed background
 * ([MotoTracker.colors.bg]) with a status line below it driven by [phase].
 * The animation loops for as long as the composable is in the composition;
 * dismiss timing is governed by [SplashGate] at the call site.
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
    val image = AnimatedImageVector.animatedVectorResource(R.drawable.avd_moto_splash)
    var atEnd by remember { mutableStateOf(false) }

    // Toggle atEnd every 1 200 ms to drive the AVD back-and-forth (loop effect).
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_200L)
            atEnd = !atEnd
        }
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
            Image(
                painter = rememberAnimatedVectorPainter(animatedImageVector = image, atEnd = atEnd),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
            )

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = stringResource(SplashStatus.labelFor(phase)),
                // Reduced alpha for a subtler caption below the animation mark.
                color = MotoTracker.colors.dim.copy(alpha = 0.55f),
                style = MotoTracker.typography.label,
            )
        }
    }
}
