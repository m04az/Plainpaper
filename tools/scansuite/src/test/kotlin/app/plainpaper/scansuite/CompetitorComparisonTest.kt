package app.plainpaper.scansuite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompetitorComparisonTest {
    private fun row(
        id: String,
        whiteness: Double,
        legibility: Double?,
        competitor: Map<String, CompetitorMetrics>,
    ) = MetricRow(
        id = id,
        tags = setOf(Category.English),
        language = Language.English,
        synthetic = false,
        outcome = "Success",
        whiteness = whiteness,
        legibility = legibility,
        competitor = competitor,
    )

    @Test
    fun matchesOrBeatsCountsWhitenessAndLegibilityTogether() {
        val rows =
            listOf(
                row("a", 0.99, 0.98, mapOf("adobe-scan" to CompetitorMetrics(0.98, 0.97))), // beats both
                row("b", 0.95, 0.98, mapOf("adobe-scan" to CompetitorMetrics(0.98, 0.90))), // loses whiteness
                // legibility unscored on our side: whiteness decides alone
                row("c", 0.99, null, mapOf("adobe-scan" to CompetitorMetrics(0.98, 0.99))),
                row("d", 0.99, 0.80, mapOf("adobe-scan" to CompetitorMetrics(0.98, 0.95))), // loses legibility
            )
        val summary = CompetitorComparison.summarise(rows).single()
        assertEquals("adobe-scan", summary.app)
        assertEquals(2, summary.matchesOrBeats)
        assertEquals(4, summary.total)
        // Worst relative delta: legibility of row d, (0.80 - 0.95) / 0.95
        assertEquals((0.80 - 0.95) / 0.95, summary.worstRelativeDelta!!, 1e-9)
    }

    @Test
    fun appsWithoutOutputsAreOmitted() {
        val rows = listOf(row("a", 0.99, 0.98, mapOf("camscanner" to CompetitorMetrics(0.9, null))))
        val summary = CompetitorComparison.summarise(rows)
        assertEquals(listOf("camscanner"), summary.map { it.app })
        assertTrue(summary.single().matchesOrBeats == 1)
    }
}
