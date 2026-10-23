package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.scanengine.model.OrientationConfidence

/**
 * The decision rule of the native orientation stage (research R6), kept in Kotlin so the rule
 * itself is unit-tested and reusable by diagnostics; the native code computes the inputs.
 */
public object OrientationVote {
    /** Column-profile variance must exceed the row-profile variance by this ratio to call lines vertical. */
    public const val AXIS_RATIO: Double = 1.3
    public const val MIN_LINES: Int = 3
    public const val FLIP_THRESHOLD: Double = 0.60
    public const val HIGH_CONFIDENCE_LINES: Int = 6
    public const val HIGH_CONFIDENCE_AGREEMENT: Double = 0.75
    private const val QUARTER_TURN = 90
    private const val HALF_TURN = 180
    private const val FULL_TURN = 360

    /** One text line: its ink mass and whether its cues say "upside down". */
    public data class LineVote(
        val mass: Double,
        val upsideDown: Boolean,
    )

    public data class Decision(
        val rotation: Int,
        val confidence: OrientationConfidence,
    )

    /** Portrait/landscape choice: 90 when text lines run vertically. */
    public fun axisRotation(
        rowVariance: Double,
        columnVariance: Double,
    ): Int = if (columnVariance > AXIS_RATIO * rowVariance) QUARTER_TURN else 0

    public fun decide(
        axisRotation: Int,
        lines: List<LineVote>,
    ): Decision {
        if (lines.size < MIN_LINES) return Decision(axisRotation, OrientationConfidence.None)
        val total = lines.sumOf { it.mass }
        if (total <= 0.0) return Decision(axisRotation, OrientationConfidence.None)
        val flipped = lines.filter { it.upsideDown }.sumOf { it.mass } / total
        val rotation = if (flipped >= FLIP_THRESHOLD) (axisRotation + HALF_TURN) % FULL_TURN else axisRotation
        val agreement = maxOf(flipped, 1.0 - flipped)
        val confidence =
            if (lines.size >= HIGH_CONFIDENCE_LINES && agreement >= HIGH_CONFIDENCE_AGREEMENT) {
                OrientationConfidence.High
            } else {
                OrientationConfidence.Low
            }
        return Decision(rotation, confidence)
    }
}
