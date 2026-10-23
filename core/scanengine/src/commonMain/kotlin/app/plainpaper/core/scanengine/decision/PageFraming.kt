package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.PixelSize
import app.plainpaper.core.scanengine.model.FitMode
import kotlin.math.min
import kotlin.math.roundToInt

/** Placement of the corrected page inside the output frame (research R8). */
public data class ContentRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

public object PageFraming {
    /** Output pixel size for a page size given the detected page proportions. */
    public fun outputSize(
        pageSize: PageSize,
        detectedAspect: Double,
        landscape: Boolean,
    ): PixelSize = pageSize.outputPixels(detectedAspect, landscape)

    /** Natural pages fill the frame; fixed sizes are centred on white margins. */
    public fun fitFor(pageSize: PageSize): FitMode =
        if (pageSize == PageSize.Natural) FitMode.Natural else FitMode.Fit

    /** Where the page lands inside the frame when centred with its aspect preserved. */
    public fun contentRect(
        pageAspect: Double,
        output: PixelSize,
    ): ContentRect {
        val frameAspect = output.width.toDouble() / output.height
        val width: Int
        val height: Int
        if (pageAspect >= frameAspect) {
            width = output.width
            height = min(output.height, (output.width / pageAspect).roundToInt())
        } else {
            height = output.height
            width = min(output.width, (output.height * pageAspect).roundToInt())
        }
        return ContentRect(
            x = (output.width - width) / 2,
            y = (output.height - height) / 2,
            width = width,
            height = height,
        )
    }
}
