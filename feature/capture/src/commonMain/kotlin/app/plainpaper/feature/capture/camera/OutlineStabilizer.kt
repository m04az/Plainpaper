package app.plainpaper.feature.capture.camera

import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad
import kotlin.math.hypot

/**
 * Turns per-frame detections into a calm on-screen outline (fixes the flicker seen on a phone):
 * - a new outline appears only after [confirmFrames] consecutive detections that agree;
 * - while shown, corners move by an exponential moving average ([smoothing]);
 * - a detection far from the shown outline must repeat [confirmFrames] times before it replaces it;
 * - when detection misses, the last outline stays for [holdMillis] before it disappears.
 * Distances are fractions of the frame diagonal. Frame pixels in, frame pixels out.
 */
class OutlineStabilizer(
    private val frameDiagonal: Float,
    private val confirmFrames: Int = DEFAULT_CONFIRM_FRAMES,
    private val holdMillis: Long = DEFAULT_HOLD_MILLIS,
    private val smoothing: Float = DEFAULT_SMOOTHING,
    private val sameQuadFraction: Float = DEFAULT_SAME_QUAD,
) {
    private var shown: Quad? = null
    private var lastSeenMillis: Long = 0L
    private var candidate: Quad? = null
    private var candidateCount = 0

    /** The outline to draw after this frame, or null. */
    fun onFrame(
        timestampMillis: Long,
        corners: Quad?,
    ): Quad? {
        if (corners == null) return onMiss(timestampMillis)
        val current = shown
        if (current != null && drift(current, corners) <= sameQuadFraction) {
            shown = blend(current, corners)
            lastSeenMillis = timestampMillis
            candidate = null
            candidateCount = 0
            return shown
        }
        // Either nothing is shown yet or the page jumped: confirm before switching.
        val pending = candidate
        if (pending != null && drift(pending, corners) <= sameQuadFraction) {
            candidateCount++
            candidate = blend(pending, corners)
        } else {
            candidate = corners
            candidateCount = 1
        }
        if (candidateCount >= confirmFrames) {
            shown = candidate
            lastSeenMillis = timestampMillis
            candidate = null
            candidateCount = 0
            return shown
        }
        // An unconfirmed jump keeps the old outline until it expires.
        return if (current != null &&
            timestampMillis - lastSeenMillis <= holdMillis
        ) {
            current
        } else {
            null.also { shown = null }
        }
    }

    fun reset() {
        shown = null
        candidate = null
        candidateCount = 0
    }

    private fun onMiss(timestampMillis: Long): Quad? {
        candidate = null
        candidateCount = 0
        val current = shown ?: return null
        if (timestampMillis - lastSeenMillis > holdMillis) {
            shown = null
            return null
        }
        return current
    }

    private fun blend(
        from: Quad,
        to: Quad,
    ): Quad {
        val a = from.points
        val b = to.points
        val p =
            List(a.size) { i -> Point(a[i].x + smoothing * (b[i].x - a[i].x), a[i].y + smoothing * (b[i].y - a[i].y)) }
        return Quad(p[0], p[1], p[2], p[3])
    }

    private fun drift(
        a: Quad,
        b: Quad,
    ): Float {
        val pa = a.points
        val pb = b.points
        var worst = 0f
        for (i in pa.indices) worst = maxOf(worst, hypot(pa[i].x - pb[i].x, pa[i].y - pb[i].y))
        return worst / frameDiagonal
    }

    companion object {
        const val DEFAULT_CONFIRM_FRAMES = 2
        const val DEFAULT_HOLD_MILLIS = 400L
        const val DEFAULT_SMOOTHING = 0.5f
        const val DEFAULT_SAME_QUAD = 0.12f
    }
}
