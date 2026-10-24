package app.plainpaper.core.capture.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.plainpaper.core.domain.model.capture.CameraPermissionState
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.coroutines.resume

/** AVCaptureDevice authorization (research R9). iOS never prompts twice: a refusal is permanent. */
internal class IosCameraPermission : CameraPermission {
    override fun status(): CameraPermissionState {
        val camera =
            AVCaptureDevice.defaultDeviceWithDeviceType(
                AVCaptureDeviceTypeBuiltInWideAngleCamera,
                mediaType = AVMediaTypeVideo,
                position = AVCaptureDevicePositionBack,
            )
        if (camera == null) return CameraPermissionState.Unavailable
        return when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> CameraPermissionState.Granted
            AVAuthorizationStatusNotDetermined -> CameraPermissionState.NotAsked
            else -> CameraPermissionState.DeniedPermanently
        }
    }

    override suspend fun request(): CameraPermissionState {
        if (status() != CameraPermissionState.NotAsked) return status()
        suspendCancellableCoroutine { continuation ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        return status()
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

internal class IosCameraPermissionFactory : CameraPermissionFactory {
    @Composable
    override fun remember(): CameraPermission = remember { IosCameraPermission() }
}
