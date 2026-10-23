package app.plainpaper.scansuite

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Per-photo metrics (data-model.md MetricRow). */
@Serializable
data class MetricRow(
    val id: String,
    val tags: Set<Category>,
    val language: Language,
    val synthetic: Boolean,
    val outcome: String,
    val confidence: String? = null,
    val cornerError: Double? = null,
    val classifiedNotFound: Boolean? = null,
    val whiteness: Double? = null,
    val textDarkness: Double? = null,
    val legibility: Double? = null,
    val straightnessDeg: Double? = null,
    val expectedRotation: Int = 0,
    val rotationApplied: Int? = null,
    val orientationCorrect: Boolean? = null,
    val hostMillis: Long = 0,
    val peakNativeBytes: Long = 0,
    val flattenStatus: String? = null,
    val competitor: Map<String, CompetitorMetrics> = emptyMap(),
    val error: String? = null,
)

@Serializable
data class CompetitorMetrics(
    val whiteness: Double,
    val legibility: Double? = null,
)

/** One gate check with its verdict. */
@Serializable
data class Check(
    val name: String,
    val value: Double?,
    val threshold: Double,
    val higherIsBetter: Boolean,
    val enforced: Boolean,
    val samples: Int,
    val note: String = "",
) {
    val passed: Boolean
        get() =
            when {
                !enforced -> true
                value == null -> false
                higherIsBetter -> value >= threshold - EPSILON
                else -> value <= threshold + EPSILON
            }

    companion object {
        private const val EPSILON = 1e-9
    }
}

@Serializable
data class CompetitorSummary(
    val app: String,
    val matchesOrBeats: Int,
    val total: Int,
    val worstRelativeDelta: Double?,
)

@Serializable
data class QualityReport(
    val generatedAt: String,
    val engineVersion: String,
    val capability: String,
    val rows: List<MetricRow>,
    val checks: List<Check>,
    val competitorSummary: List<CompetitorSummary>,
    val cancelLatencyMillisP100: Long? = null,
) {
    val passed: Boolean
        get() = checks.all { it.passed }

    fun write(directory: File) {
        directory.mkdirs()
        File(directory, "report.json").writeText(json.encodeToString(serializer(), this))
        File(directory, "report.md").writeText(toMarkdown())
    }

    fun toMarkdown(): String =
        buildString {
            val verdict = if (passed) "✅ PASS" else "❌ FAIL"
            appendLine("# Scan engine quality report")
            appendLine()
            appendLine("Generated $generatedAt, engine $engineVersion, capability $capability. Overall: $verdict")
            appendLine()
            appendChecks()
            appendCompetitors()
            appendLegibility()
            appendLine("## Slowest photos (host)")
            appendLine()
            rows.sortedByDescending { it.hostMillis }.take(SLOWEST_COUNT).forEach {
                appendLine("- ${it.id}: ${it.hostMillis} ms")
            }
            appendLine()
            appendRows()
        }

    private fun StringBuilder.appendChecks() {
        appendLine("## Gate checks")
        appendLine()
        appendLine("| Check | Value | Threshold | Samples | Enforced | Result |")
        appendLine("|-------|-------|-----------|---------|----------|--------|")
        for (check in checks) {
            val value = check.value.fmt().ifEmpty { "n/a" }
            val op = if (check.higherIsBetter) "≥" else "≤"
            val verdict =
                if (!check.enforced) {
                    "⚪ info"
                } else if (check.passed) {
                    "✅"
                } else {
                    "❌"
                }
            val note = if (check.note.isEmpty()) "" else " (${check.note})"
            val enforced = if (check.enforced) "yes" else "no"
            appendLine(
                "| ${check.name} | $value | $op ${check.threshold.fmt()} | ${check.samples} | $enforced | $verdict$note |",
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendCompetitors() {
        if (competitorSummary.isEmpty()) return
        appendLine("## Competitor comparison")
        appendLine()
        appendLine("| App | Matches or beats | Worst relative delta |")
        appendLine("|-----|------------------|----------------------|")
        for (c in competitorSummary) {
            appendLine(
                "| ${c.app} | ${c.matchesOrBeats} / ${c.total} | ${c.worstRelativeDelta.fmt().ifEmpty { "n/a" }} |",
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendLegibility() {
        appendLine("## Per-language legibility")
        appendLine()
        for (language in Language.entries) {
            val values = rows.filter { it.language == language }.mapNotNull { it.legibility }
            if (values.isNotEmpty()) appendLine("- $language: ${values.average().fmt()} (${values.size} photos)")
        }
        appendLine()
    }

    private fun StringBuilder.appendRows() {
        appendLine("## Rows")
        appendLine()
        appendLine(
            "| Id | Outcome | Conf | Corner err | NotFound | White | Dark | Skew° | Rot (exp/got) | Legibility | ms | Error |",
        )
        appendLine(
            "|----|---------|------|------------|----------|-------|------|-------|---------------|------------|----|-------|",
        )
        for (row in rows) {
            val cells =
                listOf(
                    row.id,
                    row.outcome,
                    row.confidence ?: "",
                    row.cornerError.fmt(),
                    row.classifiedNotFound?.toString() ?: "",
                    row.whiteness.fmt(),
                    row.textDarkness.fmt(),
                    row.straightnessDeg.fmt(),
                    "${row.expectedRotation}/${row.rotationApplied ?: "-"}",
                    row.legibility.fmt(),
                    row.hostMillis.toString(),
                    row.error ?: "",
                )
            appendLine(cells.joinToString(" | ", prefix = "| ", postfix = " |"))
        }
    }

    private fun Double?.fmt(): String = this?.let { "%.3f".format(it) } ?: ""

    companion object {
        private const val SLOWEST_COUNT = 5
        private val json =
            Json {
                prettyPrint = true
                encodeDefaults = true
            }

        fun read(file: File): QualityReport = json.decodeFromString(serializer(), file.readText())
    }
}
