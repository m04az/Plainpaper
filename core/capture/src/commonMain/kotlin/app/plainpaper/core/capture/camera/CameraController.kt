package app.plainpaper.core.capture.camera

import app.plainpaper.core.scanengine.PreviewFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public enum class CameraError { PermissionMissing, Unavailable, InUse, Internal }

public sealed interface CameraState {
    public data object Idle : CameraState

    public data object Starting : CameraState

    public data class Running(
        val hasFlash: Boolean,
    ) : CameraState

    public data class Error(
        val reason: CameraError,
    ) : CameraState
}

public enum class CaptureFailure { NotRunning, Storage, Internal }

public sealed interface CaptureResult {
    public data class Ready(
        val originalPath: String,
        val width: Int,
        val height: Int,
    ) : CaptureResult

    public data class Failed(
        val reason: CaptureFailure,
    ) : CaptureResult
}

/**
 * One analysis frame with the rotation that makes it upright on screen (CameraX semantics).
 * The collector calls [release] once it is done reading the pixels so the buffer can be reused.
 */
public class CameraFrame(
    public val frame: PreviewFrame,
    public val rotationDegrees: Int,
    private val onRelease: () -> Unit = {},
) {
    public fun release() {
        onRelease()
    }
}

/**
 * The platform camera behind one interface (research R1/R2). Frames are valid only inside the
 * collector; the preview surface is drawn by [CameraPreviewRenderer].
 */
public interface CameraController {
    public val state: StateFlow<CameraState>

    /** Analysis frames (≈ 640 px long edge), conflated: a slow collector sees the latest only. */
    public val frames: Flow<CameraFrame>

    /** Opens the camera; captured JPEGs are written into [outputDirectory]. */
    public suspend fun start(outputDirectory: String)

    public fun stop()

    public fun setFlash(enabled: Boolean)

    /** Takes a full-resolution photo (≤ 12 MP). */
    public suspend fun capture(): CaptureResult
}

public fun interface CameraControllerFactory {
    public fun create(): CameraController
}
