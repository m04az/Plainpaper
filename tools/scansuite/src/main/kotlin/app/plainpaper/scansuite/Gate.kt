package app.plainpaper.scansuite

import kotlin.math.abs

/** Turns metric rows into gate checks (contracts/reference-manifest.md). */
object Gate {
    fun evaluate(
        rows: List<MetricRow>,
        thresholds: Thresholds,
    ): List<Check> {
        val real = rows.filter { !it.synthetic }
        val synthetic = rows.filter { it.synthetic }
        return syntheticChecks(synthetic, thresholds) +
            realGeometryChecks(real, thresholds) +
            realToneAndTextChecks(real, thresholds) +
            realLegibilityChecks(real, thresholds) +
            timingCheck(rows, thresholds)
    }

    /** Synthetic photos have exact ground truth: always enforced. */
    private fun syntheticChecks(
        synthetic: List<MetricRow>,
        thresholds: Thresholds,
    ): List<Check> {
        val enforced = synthetic.isNotEmpty()
        val corners = synthetic.filter { it.cornerError != null }
        val oriented = synthetic.filter { it.orientationCorrect != null }
        val scan = synthetic.filter { it.whiteness != null }
        val legibility = synthetic.mapNotNull { it.legibility }
        return listOf(
            rate(
                "synthetic corners within ${thresholds.syntheticCornerErrorMax}",
                corners,
                thresholds.syntheticCornerWithinRate,
                enforced,
            ) {
                it.cornerError!! <= thresholds.syntheticCornerErrorMax
            },
            rate("synthetic orientation accuracy", oriented, thresholds.syntheticOrientationAccuracyMin, enforced) {
                it.orientationCorrect == true
            },
            rate(
                "synthetic scan style (white ≥ ${thresholds.whitenessMin}, ink ≤ ${thresholds.textDarknessMax})",
                scan,
                thresholds.styleRate,
                enforced,
            ) {
                scanStyleOk(it, thresholds)
            },
            Check(
                "synthetic legibility (relative)",
                legibility.averageOrNull(),
                thresholds.syntheticLegibilityRelativeMin,
                higherIsBetter = true,
                enforced = legibility.isNotEmpty(),
                samples = legibility.size,
                note = if (legibility.isEmpty()) "tesseract not run" else "",
            ),
        )
    }

    /** Real photos: each check is enforced once its category has enough photos. */
    private fun realGeometryChecks(
        real: List<MetricRow>,
        thresholds: Thresholds,
    ): List<Check> {
        val minPhotos = thresholds.minPhotosPerCategory
        val withCorners = real.filter { it.cornerError != null }
        val noPage = real.filter { Category.NoPage in it.tags }
        val oriented = real.filter { it.orientationCorrect != null }
        val noText = real.filter { it.language == Language.None && it.rotationApplied != null }
        val curled = real.filter { Category.Curled in it.tags && it.straightnessDeg != null }
        return listOf(
            rate(
                "real corners within ${thresholds.cornerErrorMax} (SC-001)",
                withCorners,
                thresholds.cornerWithinRate,
                withCorners.size >= minPhotos,
            ) {
                it.cornerError!! <= thresholds.cornerErrorMax
            },
            rate(
                "real no-page classified not found (SC-001)",
                noPage,
                thresholds.noPageClassifiedRate,
                noPage.size >= minPhotos,
            ) {
                it.classifiedNotFound == true
            },
            rate(
                "real orientation accuracy (SC-010)",
                oriented,
                thresholds.orientationAccuracyMin,
                oriented.size >= minPhotos,
            ) {
                it.orientationCorrect == true
            },
            rate("real no-text photos never rotated (SC-010)", noText, 1.0, noText.size >= minPhotos) {
                it.rotationApplied ==
                    0
            },
            Check(
                "real curled straightness ° (SC-004)",
                curled.map { abs(it.straightnessDeg!!) }.averageOrNull(),
                thresholds.straightnessMaxDeg,
                higherIsBetter = false,
                enforced = curled.size >= minPhotos,
                samples = curled.size,
            ),
        )
    }

    private fun realToneAndTextChecks(
        real: List<MetricRow>,
        thresholds: Thresholds,
    ): List<Check> {
        val scan = real.filter { it.whiteness != null }
        return listOf(
            rate("real scan style (SC-002)", scan, thresholds.styleRate, scan.size >= thresholds.minPhotosPerCategory) {
                scanStyleOk(it, thresholds)
            },
        )
    }

    private fun realLegibilityChecks(
        real: List<MetricRow>,
        thresholds: Thresholds,
    ): List<Check> {
        val minPhotos = thresholds.minPhotosPerCategory
        val perLanguage =
            listOf(Language.Arabic, Language.English, Language.French).map { language ->
                val values = real.filter { it.language == language }.mapNotNull { it.legibility }
                Check(
                    "real legibility $language (SC-003)",
                    values.averageOrNull(),
                    thresholds.legibilitySubsetMin,
                    higherIsBetter = true,
                    enforced = values.size >= minPhotos,
                    samples = values.size,
                )
            }
        val all = real.filter { it.language != Language.None }.mapNotNull { it.legibility }
        return perLanguage +
            Check(
                "real legibility average (SC-003)",
                all.averageOrNull(),
                thresholds.legibilityRelativeMin,
                higherIsBetter = true,
                enforced = all.size >= minPhotos,
                samples = all.size,
            )
    }

    private fun timingCheck(
        rows: List<MetricRow>,
        thresholds: Thresholds,
    ): Check {
        val timings = rows.map { it.hostMillis.toDouble() }.sorted()
        return Check(
            "host median ms (informational)",
            timings.getOrNull(timings.size / 2),
            thresholds.hostMedianMillisInfoOnly.toDouble(),
            higherIsBetter = false,
            enforced = false,
            samples = timings.size,
        )
    }

    private fun scanStyleOk(
        row: MetricRow,
        thresholds: Thresholds,
    ): Boolean =
        row.whiteness!! >= thresholds.whitenessMin &&
            (row.textDarkness == null || row.textDarkness <= thresholds.textDarknessMax)

    private fun rate(
        name: String,
        rows: List<MetricRow>,
        threshold: Double,
        enforced: Boolean,
        predicate: (MetricRow) -> Boolean,
    ): Check {
        val value = if (rows.isEmpty()) null else rows.count(predicate).toDouble() / rows.size
        return Check(
            name,
            value,
            threshold,
            higherIsBetter = true,
            enforced = enforced && rows.isNotEmpty(),
            samples = rows.size,
        )
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
