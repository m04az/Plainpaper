package app.plainpaper.scansuite

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Gate thresholds (contracts/reference-manifest.md); loaded from `thresholds.json`. */
@Serializable
data class Thresholds(
    val cornerErrorMax: Double,
    val cornerWithinRate: Double,
    val noPageClassifiedRate: Double,
    val whitenessMin: Double,
    val textDarknessMax: Double,
    val styleRate: Double,
    val legibilityRelativeMin: Double,
    val legibilitySubsetMin: Double,
    val straightnessMaxDeg: Double,
    val orientationAccuracyMin: Double,
    val competitorMatchOrBeatRate: Double,
    val competitorWorstRelative: Double,
    val cancelLatencyMaxMillis: Int,
    val cancelTrials: Int,
    val hostMedianMillisInfoOnly: Int,
    val syntheticCornerErrorMax: Double,
    val syntheticCornerWithinRate: Double,
    val syntheticOrientationAccuracyMin: Double,
    val syntheticLegibilityRelativeMin: Double,
    val minPhotosPerCategory: Int,
) {
    companion object {
        private val json =
            Json {
                allowComments = true
                ignoreUnknownKeys = true
            }

        fun load(): Thresholds {
            val text =
                requireNotNull(Thresholds::class.java.getResource("/thresholds.json")) { "thresholds.json missing" }
                    .readText()
            return json.decodeFromString(serializer(), text)
        }
    }
}
