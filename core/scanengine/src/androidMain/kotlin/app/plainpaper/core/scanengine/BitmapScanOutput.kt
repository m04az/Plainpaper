package app.plainpaper.core.scanengine

import android.graphics.Bitmap
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.scanengine.model.FallbackReason
import app.plainpaper.core.scanengine.model.StepTiming

/** Rendered page as a [Bitmap]: ALPHA_8 for 1-channel styles, ARGB_8888 for 4-channel styles. */
public class BitmapScanOutput(
    public val bitmap: Bitmap,
    override val channels: Int,
    override val styleUsed: OutputStyle,
    override val fallbackReason: FallbackReason?,
    override val steps: List<StepTiming>,
) : ScanOutput {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
}
