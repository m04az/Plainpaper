package app.plainpaper.scansuite

import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.ImageIoPhotoDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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

    /** SC-007: cancellation stops within 200 ms and never delivers a result (50 random cut-offs). */
    @Test
    fun cancelLatency() =
        runBlocking {
            val thresholds = Thresholds.load()
            val synthetic = SuiteResults.sources.first { it.synthetic }
            val photo = synthetic.manifest.photos.first { it.expectedRotation == 0 && Category.NoPage !in it.tags }
            val decoded =
                assertIs<DecodeResult.Ready>(
                    ImageIoPhotoDecoder(HostDispatchers()).decode(File(synthetic.root, photo.file).path),
                ).photo
            val engine = SuiteRunner(File(SuiteResults.outputDir, "cancel")).engine
            // Measure one full analysis to spread the cut-offs over the whole duration.
            val full =
                measureNanoTime { assertIs<AnalyzeResult.Ready>(engine.analyze(decoded)).analysis.close() } / 1_000_000
            val random = Random(7)
            var worst = 0L
            repeat(thresholds.cancelTrials) { trial ->
                val delayMillis = random.nextLong(0, maxOf(1, full))
                var deliveredAt = 0L
                val job =
                    launch(Dispatchers.Default) {
                        val result = engine.analyze(decoded)
                        deliveredAt = System.nanoTime()
                        (result as? AnalyzeResult.Ready)?.analysis?.close()
                    }
                delay(delayMillis)
                val cancelledAt = System.nanoTime()
                job.cancelAndJoin()
                val latency = (System.nanoTime() - cancelledAt) / 1_000_000
                worst = maxOf(worst, latency)
                // A result may only arrive if the analysis finished before the cancel request.
                assertFalse(
                    deliveredAt > cancelledAt,
                    "trial $trial delivered a result after cancellation at $delayMillis ms",
                )
                assertTrue(
                    latency <= thresholds.cancelLatencyMaxMillis,
                    "trial $trial: cancel took $latency ms (cut at $delayMillis ms)",
                )
            }
            println("cancel latency p100: $worst ms over ${thresholds.cancelTrials} trials (full run $full ms)")
        }
}
