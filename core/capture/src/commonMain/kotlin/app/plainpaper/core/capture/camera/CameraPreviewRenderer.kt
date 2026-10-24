package app.plainpaper.core.capture.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Draws the live preview of a [CameraController] (CameraX viewfinder / AVCaptureVideoPreviewLayer). */
public interface CameraPreviewRenderer {
    @Composable
    public fun Render(
        controller: CameraController,
        modifier: Modifier,
    )
}
