package app.plainpaper.scansuite.metrics

import app.plainpaper.core.scanengine.model.Quad
import kotlin.math.hypot

/** Corner detection error as a fraction of the photo diagonal (SC-001). */
object CornerError {
    /**
     * Max corner distance between the expected corners (TL, TR, BR, BL) and the detected quad,
     * minimised over the four cyclic orderings so a page detected with a different "top" edge
     * still scores on geometry alone, divided by the photo diagonal.
     */
    fun fractionOfDiagonal(
        expected: List<List<Double>>,
        detected: Quad,
        photoWidth: Int,
        photoHeight: Int,
    ): Double {
        val diagonal = hypot(photoWidth.toDouble(), photoHeight.toDouble())
        val detectedPoints = detected.points
        var best = Double.MAX_VALUE
        for (shift in 0 until 4) {
            var worst = 0.0
            for (i in 0 until 4) {
                val e = expected[i]
                val d = detectedPoints[(i + shift) % 4]
                worst = maxOf(worst, hypot(e[0] - d.x, e[1] - d.y))
            }
            best = minOf(best, worst)
        }
        return best / diagonal
    }
}
