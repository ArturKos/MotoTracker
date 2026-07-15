package com.mototracker.ui.screens.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin, testable factory that builds an [Intent.ACTION_SEND] share intent for a share-card
 * image file.
 *
 * Split into two methods so the URI resolution (which requires a real [FileProvider] registration)
 * stays separate from the intent construction (which is pure and Robolectric-testable):
 * - [imageUri] wraps [FileProvider.getUriForFile] — on-device only (🔬).
 * - [buildIntent] constructs the [Intent] given an already-resolved [Uri] — fully unit-testable.
 */
@Singleton
class RideShareCardShareIntentFactory @Inject constructor() {

    /**
     * Returns a `content://` URI for [file] via [FileProvider].
     *
     * Requires the `<cache-path name="share-cards" …>` entry to be declared in
     * `res/xml/file_paths.xml` and the FileProvider to be registered in the manifest
     * under the authority `<applicationId>.fileprovider`.
     *
     * @param context     Android context (application or activity).
     * @param file        The rendered PNG file to share (typically in `cacheDir/share-cards/`).
     * @return            A `content://` URI the system share sheet can read.
     */
    fun imageUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Builds an [Intent.ACTION_SEND] intent for sharing [imageUri] as a PNG image.
     *
     * Sets [Intent.FLAG_GRANT_READ_URI_PERMISSION] so the receiving app can read the
     * `content://` URI without holding a storage permission.
     *
     * @param imageUri A `content://` URI pointing at the share-card PNG.
     * @return         A configured [Intent.ACTION_SEND] intent ready for [Intent.createChooser].
     */
    fun buildIntent(imageUri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
