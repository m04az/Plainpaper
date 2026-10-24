package app.plainpaper.feature.capture.camera

import app.plainpaper.core.domain.model.Confidence
import app.plainpaper.core.domain.model.Quad
import kotlin.math.hypot

/** One detection result with its frame time (data-model.md). */
public data class DetectionSample(
    val timestampMillis: Long,
    val corners: Quad?,
    val confidence: Confidence,
    val fillFraction: Float,
    val meanLuma: Float,
)

public sealed interface GateState {
    /** No page in view; [prolonged] after [AutoCaptureGate.prolongedNoPageMillis] without one. */
    data class NoPage(
        val prolonged: Boolean = false,
    ) : GateState

    /** A page, but it is too small (or detection is unusable): adjust or tap the shutter. */
    data object LowConfidence : GateState

    data object TooDark : GateState

    /** Steady page; [progress] 0..1 towards the automatic capture. */
    data class Tracking(
        val progress: Float,
    ) : GateState

    /** Take the photo now (returned exactly once per steady page). */
    data object Capture : GateState

    /** Captured; waits for the page to move or leave before arming again. */
    data object Cooldown : GateState
}

/**
 * Steadiness gate for automatic capture (research R5, spec FR-003): a detected page (low confidence needs a window
 * [LOW_CONFIDENCE_WINDOW_FACTOR] times longer), enough fill, enough light, and corners that stay within [maxDriftFraction] of the frame diagonal
 * for [steadyWindowMillis]. After a capture it stays in [GateState.Cooldown] until the page
 * moves more than [cooldownDriftFraction] of the diagonal or disappears and comes back.
 */
public class AutoCaptureGate(
    private val frameDiagonal: Float,
    private val steadyWindowMillis: Long = DEFAULT_STEADY_WINDOW_MILLIS,
    private val maxDriftFraction: Float = DEFAULT_MAX_DRIFT,
    private val minFill: Float = DEFAULT_MIN_FILL,
    private val darkLuma: Float = DEFAULT_DARK_LUMA,
    private val cooldownDriftFraction: Float = DEFAULT_COOLDOWN_DRIFT,
    private val prolongedNoPageMillis: Long = DEFAULT_PROLONGED_NO_PAGE_MILLIS,
) {
    /** Auto toggle; when false the gate tracks but never returns [GateState.Capture]. */
    var enabled: Boolean = true

    private var anchorCorners: Quad? = null
    private var anchorTime: Long = 0L
    private var trackHasLow = false
    private var cooldownCorners: Quad? = null
    private var pageLostDuringCooldown = false
    private var noPageSince: Long? = null

    fun onSample(sample: DetectionSample): GateState {
        val corners = sample.corners
        return when {
            sample.meanLuma < darkLuma -> {
                clearAnchor()
                GateState.TooDark
            }

            corners == null -> {
                noPage(sample.timestampMillis)
            }

            inCooldown(corners) -> {
                GateState.Cooldown
            }

            sample.confidence == Confidence.NotFound || sample.fillFraction < minFill -> {
                clearAnchor()
                GateState.LowConfidence
            }

            else -> {
                track(sample.timestampMillis, corners, sample.confidence == Confidence.Low)
            }
        }
    }

    private fun noPage(timestampMillis: Long): GateState {
        clearAnchor()
        if (cooldownCorners != null) pageLostDuringCooldown = true
        val since = noPageSince ?: timestampMillis.also { noPageSince = it }
        return GateState.NoPage(prolonged = timestampMillis - since >= prolongedNoPageMillis)
    }

    /** True while the captured page is still (almost) where it was; clears the cooldown otherwise. */
    private fun inCooldown(corners: Quad): Boolean {
        noPageSince = null
        val captured = cooldownCorners ?: return false
        if (pageLostDuringCooldown || drift(captured, corners) > cooldownDriftFraction) {
            cooldownCorners = null
            pageLostDuringCooldown = false
            return false
        }
        return true
    }

    private fun track(
        timestampMillis: Long,
        corners: Quad,
        low: Boolean,
    ): GateState {
        val anchor = anchorCorners
        if (anchor == null || drift(anchor, corners) > maxDriftFraction) {
            anchorCorners = corners
            anchorTime = timestampMillis
            trackHasLow = low
            return GateState.Tracking(0f)
        }
        if (low) trackHasLow = true
        val window = if (trackHasLow) steadyWindowMillis * LOW_CONFIDENCE_WINDOW_FACTOR else steadyWindowMillis
        val progress = ((timestampMillis - anchorTime).toFloat() / window).coerceIn(0f, 1f)
        if (progress >= 1f && enabled) {
            enterCooldown(corners)
            return GateState.Capture
        }
        return GateState.Tracking(progress)
    }

    /** Arms the cooldown after a manual capture so the same page is not auto-captured at once. */
    fun enterCooldown(corners: Quad?) {
        cooldownCorners = corners ?: anchorCorners
        pageLostDuringCooldown = false
        clearAnchor()
    }

    fun reset() {
        clearAnchor()
        cooldownCorners = null
        pageLostDuringCooldown = false
        noPageSince = null
    }

    private fun clearAnchor() {
        anchorCorners = null
        anchorTime = 0L
        trackHasLow = false
    }

    private fun drift(
        a: Quad,
        b: Quad,
    ): Float {
        val pa = a.points
        val pb = b.points
        var worst = 0f
        for (i in pa.indices) {
            worst = maxOf(worst, hypot(pa[i].x - pb[i].x, pa[i].y - pb[i].y))
        }
        return worst / frameDiagonal
    }

    companion object {
        const val DEFAULT_STEADY_WINDOW_MILLIS = 700L
        const val SIMPLIFIED_STEADY_WINDOW_MILLIS = 1_000L
        const val DEFAULT_MAX_DRIFT = 0.015f
        const val DEFAULT_MIN_FILL = 0.05f
        const val LOW_CONFIDENCE_WINDOW_FACTOR = 2
        const val DEFAULT_DARK_LUMA = 40f
        const val DEFAULT_COOLDOWN_DRIFT = 0.10f
        const val DEFAULT_PROLONGED_NO_PAGE_MILLIS = 3_000L
    }
}
