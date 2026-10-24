package app.plainpaper.feature.capture.camera

import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.capture.CameraPermissionState
import app.plainpaper.core.domain.model.capture.CaptureMode

sealed interface CameraAction {
    data class PermissionStatus(
        val state: CameraPermissionState,
    ) : CameraAction

    data object AllowCamera : CameraAction

    data object OpenSettings : CameraAction

    data object ImportInstead : CameraAction

    data object ScreenStarted : CameraAction

    data object ScreenStopped : CameraAction

    data class PreviewSizeChanged(
        val width: Int,
        val height: Int,
    ) : CameraAction

    data object Shutter : CameraAction

    data class SetFlash(
        val enabled: Boolean,
    ) : CameraAction

    data class SetAutoCapture(
        val enabled: Boolean,
    ) : CameraAction

    data class SelectMode(
        val mode: CaptureMode,
    ) : CameraAction

    data object Done : CameraAction

    data object Close : CameraAction

    data object ConfirmDiscard : CameraAction

    data object KeepScanning : CameraAction

    data object DismissCaptureFailed : CameraAction

    data class ReviewLook(
        val style: OutputStyle,
    ) : CameraAction

    data object ReviewRotate : CameraAction

    data object ReviewFlip : CameraAction

    data object ReviewRetake : CameraAction

    data object ReviewSave : CameraAction
}

sealed interface CameraEffect {
    data object RequestPermission : CameraEffect

    data object OpenSettings : CameraEffect

    data class NavigateToImport(
        val noCamera: Boolean,
    ) : CameraEffect

    data class NavigateToHandoff(
        val sessionId: String,
    ) : CameraEffect

    data object NavigateBack : CameraEffect
}
