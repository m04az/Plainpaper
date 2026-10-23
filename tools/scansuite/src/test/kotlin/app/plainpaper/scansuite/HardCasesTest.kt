package app.plainpaper.scansuite

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.ImageIoPhotoDecoder
import app.plainpaper.core.scanengine.model.AnalyzeOptions
import app.plainpaper.core.scanengine.model.CornerSource
import app.plainpaper.core.scanengine.model.Outcome
import app.plainpaper.core.scanengine.model.Point
import app.plainpaper.core.scanengine.model.ProcessingStep
import app.plainpaper.core.scanengine.model.Quad
import app.plainpaper.core.scanengine.model.SkipReason
import app.plainpaper.core.scanengine.model.StepStatus
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** User Story 3: hard photos return usable results with honest confidence (synthetic hard scenes). */
class HardCasesTest {
    private val rows get() = SuiteResults.report.rows.filter { it.synthetic }

    private fun rowsWith(marker: String) =
        rows.filter { "-$marker-" in it.id }.also {
            assertTrue(
                it.isNotEmpty(),
                marker,
            )
        }

    @Test
    fun noPagePhotosAreClassifiedNotFound() {
        for (row in rowsWith("nopage")) {
            assertEquals("NotFound", row.confidence, row.id)
            assertEquals(true, row.classifiedNotFound, row.id)
            assertEquals(0, row.rotationApplied, row.id)
        }
    }

    @Test
    fun darkRoomsStillYieldWhitePaper() {
        for (row in rowsWith("dark").filter { "verydark" !in it.id }) {
            assertEquals("Success", row.outcome, row.id)
            assertTrue(row.cornerError!! <= 0.01, "${row.id} corners ${row.cornerError}")
            assertTrue(row.whiteness!! >= 0.97, "${row.id} whiteness ${row.whiteness}")
        }
    }

    @Test
    fun veryDarkPhotosAreReportedTooDark() {
        for (row in rowsWith("verydark")) assertEquals("TooDark", row.outcome, row.id)
    }

    @Test
    fun lowContrastBackgroundsAreStillDetected() {
        for (row in rowsWith("lowcontrast")) {
            assertTrue(row.cornerError!! <= 0.01, "${row.id} corners ${row.cornerError} (${row.confidence})")
        }
    }

    @Test
    fun curledPagesAreFlattenedOnFullCapability() {
        for (row in rowsWith("curled")) {
            assertEquals("Applied", row.flattenStatus, row.id)
            val deviation = assertNotNull(row.straightnessDeg, row.id)
            assertTrue(abs(deviation) <= 0.5, "${row.id} baseline deviation $deviation°")
        }
    }

    @Test
    fun simplifiedCapabilitySkipsFlatteningAndStillSucceeds() =
        runBlocking {
            val synthetic = SuiteResults.sources.first { it.synthetic }
            val photo = synthetic.manifest.photos.first { Category.Curled in it.tags }
            val engine = SuiteRunner(File(SuiteResults.outputDir, "simplified"), DeviceCapability.Simplified).engine
            val decoded =
                assertIs<DecodeResult.Ready>(
                    ImageIoPhotoDecoder(HostDispatchers()).decode(File(synthetic.root, photo.file).path),
                ).photo
            val analysis =
                assertIs<AnalyzeResult.Ready>(
                    engine.analyze(decoded, AnalyzeOptions(capability = DeviceCapability.Simplified)),
                ).analysis
            analysis.use {
                assertEquals(Outcome.Success, analysis.outcome)
                assertEquals(DeviceCapability.Simplified, analysis.capabilityUsed)
                val flatten = analysis.steps.first { it.step == ProcessingStep.Flatten }
                assertEquals(StepStatus.Skipped(SkipReason.Capability), flatten.status)
            }
        }

    @Test
    fun userCornersOverrideDetection() =
        runBlocking {
            val synthetic = SuiteResults.sources.first { it.synthetic }
            val photo = synthetic.manifest.photos.first { it.expectedRotation == 0 && it.expectedCorners != null }
            val expected = photo.expectedCorners!!
            val corners =
                Quad(
                    Point(expected[0][0].toFloat(), expected[0][1].toFloat()),
                    Point(expected[1][0].toFloat(), expected[1][1].toFloat()),
                    Point(expected[2][0].toFloat(), expected[2][1].toFloat()),
                    Point(expected[3][0].toFloat(), expected[3][1].toFloat()),
                )
            val engine = SuiteRunner(File(SuiteResults.outputDir, "usercorners")).engine
            val decoded =
                assertIs<DecodeResult.Ready>(
                    ImageIoPhotoDecoder(HostDispatchers()).decode(File(synthetic.root, photo.file).path),
                ).photo
            val analysis =
                assertIs<AnalyzeResult.Ready>(
                    engine.analyze(decoded, AnalyzeOptions(userCorners = corners)),
                ).analysis
            analysis.use {
                assertEquals(CornerSource.User, analysis.cornerSource)
                assertEquals(corners, analysis.corners)
                val detect = analysis.steps.first { it.step == ProcessingStep.Detect }
                assertEquals(StepStatus.Skipped(SkipReason.UserCorners), detect.status)
            }
        }
}
