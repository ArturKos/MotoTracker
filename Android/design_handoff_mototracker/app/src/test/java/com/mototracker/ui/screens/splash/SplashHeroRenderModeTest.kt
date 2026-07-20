package com.mototracker.ui.screens.splash

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [SplashHero.renderMode].
 *
 * Verifies that the pure branch-decision helper maps [allLayersPresent] to the
 * correct [SplashRenderMode] without any Android framework or Compose dependency.
 */
class SplashHeroRenderModeTest {

    @Test
    fun `renderMode returns LAYERED when allLayersPresent is true`() {
        assertEquals(
            SplashRenderMode.LAYERED,
            SplashHero.renderMode(allLayersPresent = true),
        )
    }

    @Test
    fun `renderMode returns STATIC_FALLBACK when allLayersPresent is false`() {
        assertEquals(
            SplashRenderMode.STATIC_FALLBACK,
            SplashHero.renderMode(allLayersPresent = false),
        )
    }
}
