package app.plainpaper.core.scanengine.model

/** Result of [app.plainpaper.core.scanengine.ScanEngine.detect] on one preview frame. */
public data class DetectResult(
    /** Page corners in frame pixels; null when no page was found. */
    val corners: Quad?,
    val confidence: Confidence,
    /** Quad area over frame area, 0 when not found. */
    val fillFraction: Float,
    /** Mean luminance of the frame, 0..255. */
    val meanLuma: Float,
    val micros: Long,
) {
    public companion object {
        public fun notFound(
            meanLuma: Float = 0f,
            micros: Long = 0,
        ): DetectResult = DetectResult(null, Confidence.NotFound, 0f, meanLuma, micros)
    }
}
