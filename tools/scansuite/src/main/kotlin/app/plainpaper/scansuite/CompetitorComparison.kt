package app.plainpaper.scansuite

import app.plainpaper.core.scanengine.BufferedImageScanOutput
import app.plainpaper.scansuite.metrics.GrayImage
import app.plainpaper.scansuite.metrics.ToneMetrics
import java.io.File
import javax.imageio.ImageIO

/** Competitor outputs measured with the same metrics (FR-017, SC-008). */
object CompetitorComparison {
    fun measure(
        source: ManifestSource,
        photo: ReferencePhoto,
        scan: BufferedImageScanOutput?,
        legibility: LegibilityScorer?,
    ): Map<String, CompetitorMetrics> {
        if (photo.competitors.isEmpty() || scan == null) return emptyMap()
        return photo.competitors
            .mapNotNull { (app, path) ->
                val image = runCatching { ImageIO.read(File(source.root, path)) }.getOrNull() ?: return@mapNotNull null
                val gray = GrayImage.of(image)
                val legibilityScore =
                    if (legibility != null && photo.transcript != null && photo.cleanReference != null) {
                        legibility.relativeScore(
                            image,
                            File(source.root, photo.cleanReference),
                            File(source.root, photo.transcript),
                            photo.language,
                        )
                    } else {
                        null
                    }
                app to CompetitorMetrics(whiteness = ToneMetrics.whiteness(gray), legibility = legibilityScore)
            }.toMap()
    }

    /** Per app: on how many photos Plainpaper matches or beats it, and the worst relative delta. */
    fun summarise(rows: List<MetricRow>): List<CompetitorSummary> =
        Manifest.COMPETITOR_APPS.mapNotNull { app ->
            val pairs = rows.mapNotNull { row -> row.competitor[app]?.let { row to it } }
            if (pairs.isEmpty()) return@mapNotNull null
            var wins = 0
            var worst: Double? = null
            for ((row, competitor) in pairs) {
                val ours = row.whiteness ?: continue
                val whitenessOk = ours >= competitor.whiteness
                val legibilityOk =
                    if (row.legibility != null &&
                        competitor.legibility != null
                    ) {
                        row.legibility >= competitor.legibility
                    } else {
                        true
                    }
                if (whitenessOk && legibilityOk) wins++
                val deltas = mutableListOf(relative(ours, competitor.whiteness))
                if (row.legibility != null &&
                    competitor.legibility != null
                ) {
                    deltas += relative(row.legibility, competitor.legibility)
                }
                val rowWorst = deltas.min()
                worst = if (worst == null) rowWorst else minOf(worst, rowWorst)
            }
            CompetitorSummary(app, wins, pairs.size, worst)
        }

    private fun relative(
        ours: Double,
        theirs: Double,
    ): Double = if (theirs == 0.0) 0.0 else (ours - theirs) / theirs
}
