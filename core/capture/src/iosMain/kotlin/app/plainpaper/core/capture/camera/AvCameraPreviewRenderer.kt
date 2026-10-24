package app.plainpaper.core.capture.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView

/** A UIView whose preview layer follows its bounds without implicit animations (JetBrains UIKit guide). */
@OptIn(ExperimentalForeignApi::class)
internal class PreviewView(
    private val previewLayer: AVCaptureVideoPreviewLayer,
) : UIView(frame = CGRectZero.readValue()) {
    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setValue(true, forKey = kCATransactionDisableActions)
        previewLayer.frame = bounds
        CATransaction.commit()
    }
}

/** Hosts an AVCaptureVideoPreviewLayer inside Compose (research R2). */
@OptIn(ExperimentalForeignApi::class)
internal class AvCameraPreviewRenderer : CameraPreviewRenderer {
    @Composable
    override fun Render(
        controller: CameraController,
        modifier: Modifier,
    ) {
        val av = controller as? AvCameraController ?: return
        val previewLayer =
            remember(av) {
                AVCaptureVideoPreviewLayer(session = av.session).apply {
                    videoGravity =
                        AVLayerVideoGravityResizeAspectFill
                }
            }
        UIKitView(
            factory = { PreviewView(previewLayer) },
            modifier = modifier,
            update = { },
        )
    }
}
