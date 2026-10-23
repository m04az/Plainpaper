package app.plainpaper.scansuite.metrics

/** Upright orientation check (SC-010). */
object OrientationMetric {
    fun isCorrect(
        expectedRotation: Int,
        rotationApplied: Int,
    ): Boolean = ((expectedRotation - rotationApplied) % 360 + 360) % 360 == 0
}
