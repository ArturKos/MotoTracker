package com.mototracker.ui.screens.splash

import com.mototracker.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies structural invariants of [SplashAssets.layerDrawableRes] (AF2).
 *
 * These tests ensure:
 * - Exactly nine layer IDs exist (one per splash PNG).
 * - The documented back-to-front z-order is preserved.
 * - No duplicate IDs exist, so the presence-check and renderer both address distinct assets.
 *
 * Both [SplashScreen]'s presence-check and [LayeredHero]'s bitmap renderer use
 * [SplashAssets.layerDrawableRes] as their single source of truth — these tests lock
 * that invariant so an accidental edit to [SplashAssets] is caught at compile time.
 */
class SplashAssetsTest {

    @Test
    fun `layerDrawableRes contains exactly 9 entries`() {
        assertEquals(9, SplashAssets.layerDrawableRes.size)
    }

    @Test
    fun `layerDrawableRes back-to-front z-order is mountains at 0 through tagline at 8`() {
        val ids = SplashAssets.layerDrawableRes
        assertEquals("index 0 must be mountains (back)", R.drawable.splash_l_mountains, ids[0])
        assertEquals("index 1 must be trail",            R.drawable.splash_l_trail,     ids[1])
        assertEquals("index 2 must be beam",             R.drawable.splash_l_beam,      ids[2])
        assertEquals("index 3 must be bike_body",        R.drawable.splash_l_bike_body, ids[3])
        assertEquals("index 4 must be wheel_rear",       R.drawable.splash_l_wheel_rear, ids[4])
        assertEquals("index 5 must be wheel_front",      R.drawable.splash_l_wheel_front, ids[5])
        assertEquals("index 6 must be pin",              R.drawable.splash_l_pin,       ids[6])
        assertEquals("index 7 must be wordmark",         R.drawable.splash_l_wordmark,  ids[7])
        assertEquals("index 8 must be tagline (front)",  R.drawable.splash_l_tagline,   ids[8])
    }

    @Test
    fun `layerDrawableRes has no duplicate entries`() {
        val ids = SplashAssets.layerDrawableRes
        assertEquals(
            "each splash layer must have a unique resource id",
            ids.size,
            ids.toSet().size,
        )
    }
}
