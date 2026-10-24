package app.plainpaper.core.capture.camera

import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad

/**
 * Maps points from analysis-frame pixels to the preview view (aspect-fill, centred), after
 * rotating the frame clockwise by [rotationDegrees] so it is upright (data-model.md).
 */
public data class PreviewGeometry(
    val frameWidth: Int,
    val frameHeight: Int,
    val rotationDegrees: Int,
    val viewWidth: Int,
    val viewHeight: Int,
) {
    private val swapped: Boolean = rotationDegrees % HALF_TURN != 0
    private val uprightWidth: Int = if (swapped) frameHeight else frameWidth
    private val uprightHeight: Int = if (swapped) frameWidth else frameHeight

    /** Uniform scale that fills the view with the upright frame. */
    public val scale: Float =
        maxOf(viewWidth.toFloat() / uprightWidth, viewHeight.toFloat() / uprightHeight)
    private val offsetX: Float = (viewWidth - uprightWidth * scale) / 2f
    private val offsetY: Float = (viewHeight - uprightHeight * scale) / 2f

    public fun frameToView(point: Point): Point {
        val upright =
            when (((rotationDegrees % FULL_TURN) + FULL_TURN) % FULL_TURN) {
                QUARTER_TURN -> Point(frameHeight - point.y, point.x)
                HALF_TURN -> Point(frameWidth - point.x, frameHeight - point.y)
                THREE_QUARTER_TURN -> Point(point.y, frameWidth - point.x)
                else -> point
            }
        return Point(upright.x * scale + offsetX, upright.y * scale + offsetY)
    }

    public fun frameToView(quad: Quad): Quad =
        Quad(
            frameToView(quad.topLeft),
            frameToView(quad.topRight),
            frameToView(quad.bottomRight),
            frameToView(quad.bottomLeft),
        )

    /** Length of the frame diagonal in frame pixels (drift thresholds are fractions of it). */
    public val frameDiagonal: Float = kotlin.math.hypot(frameWidth.toFloat(), frameHeight.toFloat())

    private companion object {
        const val QUARTER_TURN = 90
        const val HALF_TURN = 180
        const val THREE_QUARTER_TURN = 270
        const val FULL_TURN = 360
    }
}
