package com.mototracker.ui.screens.splash

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [SplashHero.renderMode].
 *
 * Verifies that the pure branch-decision helper maps [avdLoadSucceeded] to the
 * correct [SplashRenderMode] without any Android framework or Compose dependency.
 */
class SplashHeroRenderModeTest {

    @Test
    fun `renderMode returns ANIMATED when avdLoadSucceeded is true`() {
        assertEquals(
            SplashRenderMode.ANIMATED,
            SplashHero.renderMode(avdLoadSucceeded = true),
        )
    }

    @Test
    fun `renderMode returns STATIC_FALLBACK when avdLoadSucceeded is false`() {
        assertEquals(
            SplashRenderMode.STATIC_FALLBACK,
            SplashHero.renderMode(avdLoadSucceeded = false),
        )
    }
}
