package com.mototracker.ui.permissions

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker

/**
 * Runtime-permission handle for a single app feature, returned by [rememberFeaturePermission].
 *
 * Instances are composed fresh each recomposition, but the underlying [MutableState] objects
 * they carry are stable — reading [denied] inside a Compose scope automatically triggers
 * recomposition whenever the denial state changes.
 *
 * @property denied     `true` after the user denied the required permissions in the most recent
 *                      request; `false` until then and after a successful grant.
 * @property requestThen  Call with an [onGranted] callback.  If all required permissions are
 *                        already held, [onGranted] runs immediately.  Otherwise the system
 *                        permission dialog is shown and [onGranted] runs only on full approval.
 */
@Stable
class FeaturePermissionHandle(
    private val _denied: MutableState<Boolean>,
    val requestThen: (() -> Unit) -> Unit,
) {
    val denied: Boolean get() = _denied.value
}

/**
 * Remembers a runtime-permission handle for [feature].
 *
 * Internally wraps [ActivityResultContracts.RequestMultiplePermissions], using
 * [PermissionRequirements.permissionsFor] to build the permission list and
 * [PermissionGate.resolve] to decide whether to invoke [FeaturePermissionHandle.requestThen]
 * immediately or launch the system dialog.
 *
 * @param feature   The primary feature whose permissions are required and gated.  Only this
 *                  feature's permissions block the [FeaturePermissionHandle.requestThen] callback.
 * @param companion Additional features whose permissions are requested in the same system dialog
 *                  alongside [feature] (best-effort — their denial does not block the callback).
 *                  Defaults to empty.
 * @return A stable [FeaturePermissionHandle] for the lifetime of the composition.
 */
@Composable
fun rememberFeaturePermission(
    feature: AppFeaturePermission,
    companion: List<AppFeaturePermission> = emptyList(),
): FeaturePermissionHandle {
    val context = LocalContext.current
    val sdkInt = Build.VERSION.SDK_INT

    val required = remember(feature, sdkInt) {
        PermissionRequirements.permissionsFor(feature, sdkInt)
    }
    val extras = remember(companion, sdkInt) {
        companion.flatMap { PermissionRequirements.permissionsFor(it, sdkInt) }
    }
    val allToRequest = remember(required, extras) {
        (required + extras).distinct()
    }

    val denied = remember { mutableStateOf(false) }
    val pendingCallback = remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val allRequiredGranted = required.all { grants[it] == true }
        if (allRequiredGranted) {
            denied.value = false
            pendingCallback.value?.invoke()
        } else {
            denied.value = true
        }
        pendingCallback.value = null
    }

    return FeaturePermissionHandle(
        _denied = denied,
        requestThen = { onGranted ->
            val grantedSet = allToRequest
                .filter {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                .toSet()
            when (PermissionGate.resolve(required, grantedSet)) {
                is PermissionGateResult.Granted -> {
                    denied.value = false
                    onGranted()
                }
                is PermissionGateResult.NeedsRequest -> {
                    val missing = allToRequest.filter { it !in grantedSet }
                    pendingCallback.value = onGranted
                    launcher.launch(missing.toTypedArray())
                }
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared permission UI
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Inline banner displayed when a runtime permission has been denied or not yet granted.
 *
 * Shows [text] as a rationale and a "Grant permission" button that calls [onRetry].
 * Used as the shared denial/rationale UI across all permission-gated features.
 *
 * @param text     User-facing rationale or denial explanation (feature-specific string resource).
 * @param onRetry  Called when the user taps the grant button; should re-invoke
 *                 [FeaturePermissionHandle.requestThen] to re-launch the system dialog.
 */
@Composable
fun PermissionDeniedBanner(
    text: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MotoTracker.colors.panel, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MotoTracker.typography.bodySmall,
            color = MotoTracker.colors.text,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MotoTracker.colors.accent,
                contentColor = MotoTracker.colors.onAccent,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = stringResource(R.string.btn_grant_permission),
                style = MotoTracker.typography.routeTitle,
            )
        }
    }
}
