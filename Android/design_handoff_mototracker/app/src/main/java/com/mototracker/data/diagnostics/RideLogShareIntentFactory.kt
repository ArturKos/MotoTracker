package com.mototracker.data.diagnostics

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin, testable helper that selects the log file to share.
 *
 * Intentionally free of Android-framework calls so it can be exercised in pure JVM
 * unit tests.  [shareTargetFile] only resolves the [File]; the Composable is
 * responsible for obtaining a `content://` URI via `FileProvider.getUriForFile` and
 * launching the `Intent.ACTION_SEND` intent.  When [RideLogStore.latestLog] returns
 * null (no ride logged yet) the Composable should show a disabled state.
 */
@Singleton
class RideLogShareIntentFactory @Inject constructor() {

    /**
     * Returns the latest ride-log file suitable for sharing, or null if no log exists.
     *
     * @param store The ride-log store from which the latest log is retrieved.
     * @return The newest file in the ride-logs directory, or null when none are available.
     */
    fun shareTargetFile(store: RideLogStore): File? = store.latestLog()
}
