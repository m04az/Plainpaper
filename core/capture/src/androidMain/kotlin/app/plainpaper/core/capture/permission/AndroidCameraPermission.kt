package app.plainpaper.core.capture.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.plainpaper.core.domain.model.capture.CameraPermissionState
import kotlinx.coroutines.CompletableDeferred

private const val PREFS = "capture"
private const val KEY_ASKED = "camera_permission_asked"

/** Runtime permission through the activity result API (research R9). */
internal class AndroidCameraPermission(
    private val context: Context,
    private val activity: Activity?,
    private val launch: (CompletableDeferred<Boolean>) -> Unit,
) : CameraPermission {
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun status(): CameraPermissionState {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        return when {
            !hasCamera -> {
                CameraPermissionState.Unavailable
            }

            granted -> {
                CameraPermissionState.Granted
            }

            !prefs.getBoolean(KEY_ASKED, false) -> {
                CameraPermissionState.NotAsked
            }

            activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) -> {
                CameraPermissionState.DeniedPermanently
            }

            else -> {
                CameraPermissionState.Denied
            }
        }
    }

    override suspend fun request(): CameraPermissionState {
        if (status() == CameraPermissionState.Granted || status() == CameraPermissionState.Unavailable) return status()
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        val deferred = CompletableDeferred<Boolean>()
        launch(deferred)
        deferred.await()
        return status()
    }

    override fun openSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

internal class AndroidCameraPermissionFactory : CameraPermissionFactory {
    private class Pending {
        var deferred: CompletableDeferred<Boolean>? = null
    }

    @Composable
    override fun remember(): CameraPermission {
        val context = LocalContext.current
        val pending = remember { Pending() }
        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                pending.deferred?.complete(granted)
                pending.deferred = null
            }
        return remember(context) {
            AndroidCameraPermission(context.applicationContext, context.findActivity()) { deferred ->
                pending.deferred = deferred
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
