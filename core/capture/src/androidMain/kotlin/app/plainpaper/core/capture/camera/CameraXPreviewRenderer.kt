package app.plainpaper.core.capture.camera

import androidx.camera.compose.CameraXViewfinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/** Draws the CameraX preview with the Compose viewfinder (no AndroidView interop). */
internal class CameraXPreviewRenderer : CameraPreviewRenderer {
    @Composable
    override fun Render(
        controller: CameraController,
        modifier: Modifier,
    ) {
        val cameraX = controller as? CameraXController ?: return
        val request by cameraX.surfaceRequests.collectAsState()
        request?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
    }
}
