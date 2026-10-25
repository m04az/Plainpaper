package app.plainpaper.feature.capture.crop

import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad
import kotlin.math.min
import kotlin.math.sqrt

/** How the photo sits in the view: aspect-fit and centred. */
data class ImageFit(
    val scale: Float,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * State of the crop editor, as pure geometry: the photo of [imageWidth] x [imageHeight] pixels
 * shown aspect-fit in a [viewWidth] x [viewHeight] view, with the page border [quad] in image
 * pixels. Corner roles (top-left, top-right, bottom-right, bottom-left of the OUTPUT page) are
 * never reordered: the editor only moves points. Every operation returns a new editor.
 */
class CornerEditor(
    val imageWidth: Int,
    val imageHeight: Int,
    val viewWidth: Float,
    val viewHeight: Float,
    val quad: Quad,
    private val detected: Quad? = null,
) {
    val fit: ImageFit =
        run {
            val scale = min(viewWidth / imageWidth, viewHeight / imageHeight)
            val width = imageWidth * scale
            val height = imageHeight * scale
            ImageFit(scale, (viewWidth - width) / 2f, (viewHeight - height) / 2f, width, height)
        }

    fun toView(imagePoint: Point): Point =
        Point(
            fit.left + imagePoint.x * fit.scale,
            fit.top + imagePoint.y * fit.scale,
        )

    fun toImage(viewPoint: Point): Point =
        Point((viewPoint.x - fit.left) / fit.scale, (viewPoint.y - fit.top) / fit.scale)

    /** The four corners in view pixels, in role order. */
    fun viewCorners(): List<Point> = quad.points.map(::toView)

    /** The corner whose handle is nearest to [viewPoint] within [radius] view pixels, or null. */
    fun cornerAt(
        viewPoint: Point,
        radius: Float,
    ): Int? =
        viewCorners()
            .withIndex()
            .map { (index, corner) -> index to sqrt(squaredDistance(corner, viewPoint)).toFloat() }
            .filter { it.second <= radius }
            .minByOrNull { it.second }
            ?.first

    /**
     * Moves corner [index] by [viewDelta] view pixels, clamped to the photo. A position that
     * makes the border non-convex or smaller than [MIN_AREA_FRACTION] of the photo is refused
     * and the corner stays where it was.
     */
    fun drag(
        index: Int,
        viewDelta: Point,
    ): CornerEditor {
        val current = quad.points[index]
        val moved =
            Point(
                (current.x + viewDelta.x / fit.scale).coerceIn(0f, imageWidth.toFloat()),
                (current.y + viewDelta.y / fit.scale).coerceIn(0f, imageHeight.toFloat()),
            )
        val candidate = quad.with(index, moved)
        return if (isAcceptable(candidate)) copy(candidate) else this
    }

    /** The border the engine found, or the current one when the engine found none. */
    fun auto(): CornerEditor = copy(detected ?: quad)

    /**
     * The whole photo, with each corner role keeping the photo corner it is nearest to: the
     * roles of a rotated page are rotated too, so this is the best of the eight ways to lay the
     * photo corners on the roles without folding the quad.
     */
    fun fullPhoto(): CornerEditor {
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val photo = listOf(Point(0f, 0f), Point(w, 0f), Point(w, h), Point(0f, h))
        val current = quad.points
        val best =
            ASSIGNMENTS.minBy { order ->
                order.indices.sumOf { role -> squaredDistance(current[role], photo[order[role]]) }
            }
        return copy(Quad.fromPoints(best.map { photo[it] }))
    }

    /** The border in image pixels, ready for `PageProcessor.setCorners`. */
    fun result(): Quad = quad

    private fun copy(newQuad: Quad) = CornerEditor(imageWidth, imageHeight, viewWidth, viewHeight, newQuad, detected)

    private fun isAcceptable(candidate: Quad): Boolean =
        candidate.isConvex && candidate.area >= MIN_AREA_FRACTION * imageWidth * imageHeight

    @Suppress("MagicNumber") // four corners: TL, TR, BR, BL
    private fun Quad.Companion.fromPoints(points: List<Point>): Quad = Quad(points[0], points[1], points[2], points[3])

    private fun Quad.with(
        index: Int,
        point: Point,
    ): Quad =
        when (index) {
            0 -> copy(topLeft = point)
            1 -> copy(topRight = point)
            2 -> copy(bottomRight = point)
            else -> copy(bottomLeft = point)
        }

    private fun squaredDistance(
        a: Point,
        b: Point,
    ): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return dx * dx + dy * dy
    }

    companion object {
        /** A border covering less of the photo than this is refused. */
        const val MIN_AREA_FRACTION = 0.05

        /** The four rotations and four mirrored rotations of the corner order (all keep the quad convex). */
        private val ASSIGNMENTS =
            listOf(
                listOf(0, 1, 2, 3),
                listOf(1, 2, 3, 0),
                listOf(2, 3, 0, 1),
                listOf(3, 0, 1, 2),
                listOf(1, 0, 3, 2),
                listOf(0, 3, 2, 1),
                listOf(3, 2, 1, 0),
                listOf(2, 1, 0, 3),
            )
    }
}
