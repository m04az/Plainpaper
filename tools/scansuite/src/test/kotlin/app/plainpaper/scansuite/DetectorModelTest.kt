package app.plainpaper.scansuite

import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.ByteArrayPreviewFrame
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.ImageIoPhotoDecoder
import app.plainpaper.core.scanengine.PixelFormat
import app.plainpaper.core.scanengine.bridge.JniScanBridge
import app.plainpaper.core.scanengine.bridge.NativeStatus
import app.plainpaper.core.scanengine.bridge.loadNativeLibrary
import app.plainpaper.core.scanengine.model.AnalyzeOptions
import app.plainpaper.core.scanengine.model.CornerSource
import app.plainpaper.core.scanengine.model.DetectorKind
import app.plainpaper.core.scanengine.model.Point
import app.plainpaper.core.scanengine.model.Quad
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The detection model through the JNI bridge on the host (specs/004 tasks T010, T013). */
class DetectorModelTest {
    private val outputDir = File(SuiteResults.outputDir, "detector")

    @Test
    fun garbageAndEmptyBytesDoNotLoad() =
        runBlocking {
            val engine = SuiteRunner(outputDir, useModel = false).engine
            assertFalse(engine.loadDetector { ByteArray(4096) { (it * 31).toByte() } })
            assertFalse(engine.loadDetector { ByteArray(0) })
            assertFalse(engine.loadDetector { null })
        }

    @Test
    fun withoutAModelDetectReportsClassicAndZeroPresence() =
        runBlocking {
            val engine = SuiteRunner(outputDir, useModel = false).engine
            val width = 320
            val height = 240
            val frame =
                ByteArrayPreviewFrame(width, height, width * 4, PixelFormat.RGBA, 0L, ByteArray(width * height * 4))
            val result = engine.detect(frame)
            assertEquals(DetectorKind.Classic, result.detector)
            assertEquals(0f, result.presence)
        }

    @Test
    fun keepOrientationKeepsTheGivenCornerOrderAndDoesNotRotate() =
        runBlocking {
            val synthetic = SuiteResults.sources.first { it.synthetic }
            val photo = synthetic.manifest.photos.first { it.expectedRotation == 0 && it.expectedCorners != null }
            val expected = photo.expectedCorners!!.map { Point(it[0].toFloat(), it[1].toFloat()) }
            // Turned one step: the first corner is the page's bottom-left.
            val corners = Quad(expected[3], expected[0], expected[1], expected[2])
            val engine = SuiteRunner(outputDir, useModel = false).engine
            val decoded =
                assertIs<DecodeResult.Ready>(
                    ImageIoPhotoDecoder(HostDispatchers()).decode(File(synthetic.root, photo.file).path),
                ).photo
            val analysis =
                assertIs<AnalyzeResult.Ready>(
                    engine.analyze(decoded, AnalyzeOptions(userCorners = corners, keepOrientation = true)),
                ).analysis
            analysis.use {
                assertEquals(CornerSource.User, it.cornerSource)
                assertEquals(corners, it.corners)
                assertEquals(0, it.rotationApplied)
                assertEquals(DetectorKind.Classic, it.detector)
            }
        }

    @Test
    fun smallPreCroppedPagesAreAcceptedOnlyWithKeptOrientation() =
        runBlocking {
            // The phone's own scanner can return a small, already-cropped card (388 x 329 seen).
            val file = File.createTempFile("quickscan-small", ".jpg")
            val image = java.awt.image.BufferedImage(388, 329, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            g.color = java.awt.Color(235, 235, 230)
            g.fillRect(0, 0, 388, 329)
            g.color = java.awt.Color.DARK_GRAY
            for (y in 40 until 300 step 24) g.fillRect(30, y, 320, 6)
            g.dispose()
            javax.imageio.ImageIO.write(image, "jpg", file)
            val engine = SuiteRunner(outputDir, useModel = false).engine
            val decoded =
                assertIs<DecodeResult.Ready>(ImageIoPhotoDecoder(HostDispatchers()).decode(file.path)).photo
            val frame = Quad(Point(0f, 0f), Point(388f, 0f), Point(388f, 329f), Point(0f, 329f))
            assertIs<AnalyzeResult.Failed>(engine.analyze(decoded, AnalyzeOptions(userCorners = frame)))
            val kept =
                assertIs<AnalyzeResult.Ready>(
                    engine.analyze(decoded, AnalyzeOptions(userCorners = frame, keepOrientation = true)),
                ).analysis
            kept.use { assertEquals(0, it.rotationApplied) }
            file.delete()
        }

    // Parity with PyTorch: detector.ppdm and vectors.vec of a tools/detector-training run.
    @Test
    fun nativeInferenceMatchesTheTrainingVectors() {
        val runs = File("tools/detector-training/runs").listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
        val run = runs.lastOrNull { File(it, "detector.ppdm").isFile && File(it, "vectors.vec").isFile }
        if (run == null) {
            println("SKIP parity: no tools/detector-training/runs/*/{detector.ppdm,vectors.vec} present")
            return
        }
        loadNativeLibrary()
        val engine = JniScanBridge.nativeEngineCreate()
        try {
            val model = File(run, "detector.ppdm").readBytes()
            assertEquals(NativeStatus.Ok.code, JniScanBridge.nativeLoadDetector(engine, model))
            val vectors = ByteBuffer.wrap(File(run, "vectors.vec").readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val count = vectors.getInt()
            assertTrue(count > 0)
            var worst = 0f
            repeat(count) {
                val input = FloatArray(INPUT_FLOATS).also { vectors.asFloatBuffer().get(it) }
                vectors.position(vectors.position() + INPUT_FLOATS * 4)
                val expectedMask = FloatArray(MASK_FLOATS).also { vectors.asFloatBuffer().get(it) }
                vectors.position(vectors.position() + MASK_FLOATS * 4)
                val expectedPresence = vectors.getFloat()
                val mask = FloatArray(MASK_FLOATS)
                val presence = FloatArray(1)
                assertEquals(NativeStatus.Ok.code, JniScanBridge.nativeDetectorRun(engine, input, mask, presence))
                for (i in mask.indices) worst = maxOf(worst, abs(mask[i] - expectedMask[i]))
                worst = maxOf(worst, abs(presence[0] - expectedPresence))
            }
            println("parity ${run.name}: $count cases, worst abs diff $worst")
            assertTrue(worst < TOLERANCE, "worst abs diff $worst")
        } finally {
            JniScanBridge.nativeEngineDestroy(engine)
        }
    }

    private companion object {
        const val INPUT_FLOATS = 3 * 256 * 256
        const val MASK_FLOATS = 128 * 128
        const val TOLERANCE = 1e-4f
    }
}
