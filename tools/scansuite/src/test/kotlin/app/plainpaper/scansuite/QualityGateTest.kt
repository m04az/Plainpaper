package app.plainpaper.scansuite

import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the engine once over the real + synthetic sets; every test reads the shared report. */
object SuiteResults {
    val rootDir: File = File(System.getProperty("user.dir"))
    val outputDir: File = File(rootDir, "build/scansuite")
    val sources: List<ManifestSource> by lazy { SuiteRunner.defaultSources(rootDir, outputDir) }
    val report: QualityReport by lazy {
        SuiteRunner(outputDir, legibility = Tesseract.ifAvailable()).run(sources)
    }
}

/** The CI gate (FR-016, FR-018): every enforced threshold must hold. */
class QualityGateTest {
    @Test
    fun everyPhotoProducesARow() {
        val expected =
            SuiteResults.sources
                .flatMap { it.manifest.photos }
                .map { it.id }
                .toSet()
        assertEquals(
            expected,
            SuiteResults.report.rows
                .map { it.id }
                .toSet(),
        )
        val errors = SuiteResults.report.rows.filter { it.error != null }
        assertTrue(errors.isEmpty(), "rows with errors: " + errors.joinToString { "${it.id}: ${it.error}" })
    }

    @Test
    fun everyEnforcedCheckPasses() {
        val failing = SuiteResults.report.checks.filter { it.enforced && !it.passed }
        assertTrue(
            failing.isEmpty(),
            "Failing checks:\n" +
                failing.joinToString("\n") { "  ${it.name}: ${it.value} vs ${it.threshold} (${it.samples} samples)" } +
                "\nSee ${File(SuiteResults.outputDir, "report.md")}",
        )
    }

    @Test
    fun legibilityIsScoredWhenRequired() {
        if (System.getenv("PP_REQUIRE_OCR") == "1") {
            val scored = SuiteResults.report.rows.count { it.legibility != null }
            assertTrue(scored > 0, "PP_REQUIRE_OCR=1 but no row was scored by tesseract")
        }
    }

    @Ignore("User Story 4: cancellation latency")
    @Test
    fun cancelLatency() = Unit
}
