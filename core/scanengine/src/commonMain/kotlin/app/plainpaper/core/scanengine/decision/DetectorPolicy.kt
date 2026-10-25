package app.plainpaper.core.scanengine.decision

/**
 * Detector policy (specs/004 data-model.md): on a Simplified device the model is kept only when the
 * median of its first [SAMPLE_COUNT] preview passes is at most [MAX_MEDIAN_MICROS].
 */
internal class DetectorPolicy {
    private val samples = mutableListOf<Long>()

    /** True once the decision is made; further passes are not timed. */
    var decided: Boolean = false
        private set

    /**
     * Records the wall time of one preview pass that used the model. Returns null while still
     * measuring, otherwise whether the model stays enabled.
     */
    fun record(micros: Long): Boolean? {
        if (decided) return null
        samples += micros
        if (samples.size < SAMPLE_COUNT) return null
        decided = true
        return median(samples) <= MAX_MEDIAN_MICROS
    }

    fun reset() {
        samples.clear()
        decided = false
    }

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    companion object {
        const val SAMPLE_COUNT: Int = 5
        const val MAX_MEDIAN_MICROS: Long = 120_000
    }
}
