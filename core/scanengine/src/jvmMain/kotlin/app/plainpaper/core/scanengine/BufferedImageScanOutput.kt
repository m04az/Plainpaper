package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.scanengine.model.FallbackReason
import app.plainpaper.core.scanengine.model.StepTiming
import java.awt.image.BufferedImage

/** Rendered page as a [BufferedImage] (TYPE_BYTE_GRAY or TYPE_INT_ARGB). */
public class BufferedImageScanOutput(
    public val image: BufferedImage,
    override val channels: Int,
    override val styleUsed: OutputStyle,
    override val fallbackReason: FallbackReason?,
    override val steps: List<StepTiming>,
) : ScanOutput {
    override val width: Int get() = image.width
    override val height: Int get() = image.height
}
