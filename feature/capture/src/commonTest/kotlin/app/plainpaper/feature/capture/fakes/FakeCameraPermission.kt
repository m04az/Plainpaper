package app.plainpaper.feature.capture.fakes

import app.plainpaper.core.capture.permission.CameraPermission
import app.plainpaper.core.domain.model.capture.CameraPermissionState

class FakeCameraPermission(
    var current: CameraPermissionState = CameraPermissionState.NotAsked,
) : CameraPermission {
    /** State the next [request] resolves to; defaults to Granted. */
    var nextRequestResult: CameraPermissionState = CameraPermissionState.Granted
    var requestCount = 0
        private set
    var settingsOpened = 0
        private set

    override fun status(): CameraPermissionState = current

    override suspend fun request(): CameraPermissionState {
        requestCount++
        current = nextRequestResult
        return current
    }

    override fun openSettings() {
        settingsOpened++
    }
}
