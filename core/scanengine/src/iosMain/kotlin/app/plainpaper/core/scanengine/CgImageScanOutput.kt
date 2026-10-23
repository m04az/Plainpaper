package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.scanengine.model.FallbackReason
import app.plainpaper.core.scanengine.model.StepTiming
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGImageRef

/** Rendered page as a `CGImage` (8-bit grey or RGBA). The caller releases it with [release]. */
@OptIn(ExperimentalForeignApi::class)
public class CgImageScanOutput(
    public val image: CGImageRef,
    override val width: Int,
    override val height: Int,
    override val channels: Int,
    override val styleUsed: OutputStyle,
    override val fallbackReason: FallbackReason?,
    override val steps: List<StepTiming>,
) : ScanOutput {
    public fun release() {
        platform.CoreGraphics.CGImageRelease(image)
    }
}
