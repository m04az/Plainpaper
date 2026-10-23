package app.plainpaper.scansuite

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.ImageIoPhotoDecoder
import app.plainpaper.core.scanengine.bridge.JniScanBridge
import app.plainpaper.core.scanengine.model.AnalysisFlag
import app.plainpaper.core.scanengine.model.AnalyzeOptions
import app.plainpaper.core.scanengine.model.Outcome
import app.plainpaper.core.scanengine.model.ProcessingStep
import app.plainpaper.core.scanengine.model.SkipReason
import app.plainpaper.core.scanengine.model.StepStatus
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** User Story 4: the simplified pipeline, memory budgets and the out-of-memory retry (FR-013). */
class SimplifiedPipelineTest {
    private val outputDir = File(SuiteResults.outputDir, "simplified")

    @Test
    fun everyPhotoSucceedsOnTheSimplifiedPipelineWithinBudget() {
        val runner = SuiteRunner(outputDir, DeviceCapability.Simplified)
        val report = runner.run(SuiteResults.sources.filter { it.synthetic })
        val failed = report.rows.filter { it.error != null || it.outcome !in setOf("Success", "TooDark") }
        assertTrue(failed.isEmpty(), "failed rows: " + failed.joinToString { "${it.id}: ${it.outcome} ${it.error}" })
        for (row in report.rows) {
            if (Category.NoPage in row.tags) continue
            assertEquals("Skipped(Capability)", row.flattenStatus, row.id)
            assertTrue(row.peakNativeBytes < SIMPLIFIED_PEAK_BYTES, "${row.id} peak ${row.peakNativeBytes / 1e6} MB")
        }
    }

    @Test
    fun fullPipelineStaysWithinItsBudget() {
        for (row in SuiteResults.report.rows) {
            assertTrue(row.peakNativeBytes < FULL_PEAK_BYTES, "${row.id} peak ${row.peakNativeBytes / 1e6} MB")
            assertTrue(row.peakNativeBytes > 0, "${row.id} peak must be measured")
        }
    }

    @Test
    fun outOfMemoryIsRetriedOnceThenReported() =
        runBlocking {
            val synthetic = SuiteResults.sources.first { it.synthetic }
            val photo = synthetic.manifest.photos.first { it.expectedRotation == 0 && Category.NoPage !in it.tags }
            val decoded = decode(File(synthetic.root, photo.file))
            val engine = SuiteRunner(outputDir).engine
            try {
                JniScanBridge.nativeDebugFailAllocationAtStage(ProcessingStep.Perspective.ordinal)
                val result = engine.analyze(decoded)
                assertEquals(AnalyzeResult.Failed(Outcome.OutOfMemory), result)
            } finally {
                JniScanBridge.nativeDebugFailAllocationAtStage(-1)
            }
            // Recovery: a later call works normally and reports the retry only when one happened.
            val options = AnalyzeOptions(capability = DeviceCapability.Full)
            val ok = assertIs<AnalyzeResult.Ready>(engine.analyze(decoded, options))
            ok.analysis.use {
                assertTrue(AnalysisFlag.RetriedAtLowerResolution !in it.flags)
                val perspective = it.steps.first { s -> s.step == ProcessingStep.Perspective }
                assertEquals(StepStatus.Applied, perspective.status)
            }
        }

    @Test
    fun simplifiedSkipsFlatteningEvenWhenAllowed() =
        runBlocking {
            val synthetic = SuiteResults.sources.first { it.synthetic }
            val photo = synthetic.manifest.photos.first { Category.Curled in it.tags }
            val decoded = decode(File(synthetic.root, photo.file))
            val engine = SuiteRunner(outputDir, DeviceCapability.Simplified).engine
            val options = AnalyzeOptions(capability = DeviceCapability.Simplified, allowFlatten = true)
            val analysis = assertIs<AnalyzeResult.Ready>(engine.analyze(decoded, options)).analysis
            analysis.use {
                assertEquals(1500, it.workingLongEdgeUsed)
                val flatten = it.steps.first { s -> s.step == ProcessingStep.Flatten }
                assertEquals(StepStatus.Skipped(SkipReason.Capability), flatten.status)
            }
        }

    private fun decode(file: File) =
        runBlocking { assertIs<DecodeResult.Ready>(ImageIoPhotoDecoder(HostDispatchers()).decode(file.path)).photo }

    private companion object {
        const val SIMPLIFIED_PEAK_BYTES = 70L * 1024 * 1024
        const val FULL_PEAK_BYTES = 120L * 1024 * 1024
    }
}
