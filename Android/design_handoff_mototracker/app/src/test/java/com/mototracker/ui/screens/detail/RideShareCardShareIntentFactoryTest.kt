package com.mototracker.ui.screens.detail

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric unit tests for [RideShareCardShareIntentFactory.buildIntent].
 *
 * Tests verify the [Intent] structure produced by the pure [buildIntent] method, which
 * accepts a pre-resolved [Uri] so no [androidx.core.content.FileProvider] registration
 * is required here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RideShareCardShareIntentFactoryTest {

    private lateinit var factory: RideShareCardShareIntentFactory
    private lateinit var fakeUri: Uri

    @Before
    fun setup() {
        factory = RideShareCardShareIntentFactory()
        fakeUri = Uri.parse("content://com.mototracker.fileprovider/share-cards/route-abc12345.png")
    }

    @Test
    fun `buildIntent action is ACTION_SEND`() {
        val intent = factory.buildIntent(fakeUri)
        assertEquals(Intent.ACTION_SEND, intent.action)
    }

    @Test
    fun `buildIntent type is image png`() {
        val intent = factory.buildIntent(fakeUri)
        assertEquals("image/png", intent.type)
    }

    @Test
    fun `buildIntent includes FLAG_GRANT_READ_URI_PERMISSION`() {
        val intent = factory.buildIntent(fakeUri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `buildIntent sets EXTRA_STREAM to provided uri`() {
        val intent = factory.buildIntent(fakeUri)
        @Suppress("DEPRECATION")
        val extra = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals(fakeUri, extra)
    }
}
