package app.plainpaper.core.capture.permission

import androidx.compose.runtime.Composable
import app.plainpaper.core.domain.model.capture.CameraPermissionState

/** Camera permission as the app sees it (research R9). */
public interface CameraPermission {
    public fun status(): CameraPermissionState

    /** Shows the system prompt when the system still allows one; returns the new state. */
    public suspend fun request(): CameraPermissionState

    public fun openSettings()
}

/** Platform factory; the Android one needs the activity's result registry, hence composable. */
public interface CameraPermissionFactory {
    @Composable
    public fun remember(): CameraPermission
}
