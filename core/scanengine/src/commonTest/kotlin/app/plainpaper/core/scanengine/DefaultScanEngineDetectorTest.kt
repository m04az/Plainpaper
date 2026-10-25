package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.model.AnalyzeOptions
import app.plainpaper.core.scanengine.model.Confidence
import app.plainpaper.core.scanengine.model.DetectorKind
import app.plainpaper.core.scanengine.model.Point
import app.plainpaper.core.scanengine.model.Quad
import app.plainpaper.core.testing.FakeCapabilityProvider
import app.plainpaper.core.testing.FakeNativeBridge
import app.plainpaper.core.testing.FakeScanPhoto
import app.plainpaper.core.testing.TestDispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultScanEngineDetectorTest {
    private val bridge = FakeNativeBridge()
    private val frame = ByteArrayPreviewFrame(640, 480, 640 * 4, PixelFormat.BGRA, 1L, ByteArray(640 * 480 * 4))

    private fun engine(capability: DeviceCapability) =
        DefaultScanEngine(bridge, FakeCapabilityProvider(capability), TestDispatcherProvider())

    private fun modelDetect(micros: Long) =
        FakeNativeBridge.defaultDetect(frame, Confidence.High, DetectorKind.Model, micros)

    @Test
    fun loadDetectorPassesTheBytesToTheBridge() =
        runTest {
            val engine = engine(DeviceCapability.Full)
            val loaded = engine.loadDetector { byteArrayOf(1, 2, 3) }
            assertTrue(loaded)
            assertContentEquals(byteArrayOf(1, 2, 3), bridge.loadedDetectors.single())
        }

    @Test
    fun loadDetectorIsFalseWithoutBytes() =
        runTest {
            val engine = engine(DeviceCapability.Full)
            assertFalse(engine.loadDetector { null })
            assertTrue(bridge.loadedDetectors.isEmpty())
        }

    @Test
    fun loadDetectorIsFalseWhenTheSourceThrowsOrTheBridgeRejects() =
        runTest {
            val engine = engine(DeviceCapability.Full)
            assertFalse(engine.loadDetector { error("missing") })
            bridge.loadDetectorResult = false
            assertFalse(engine.loadDetector { byteArrayOf(9) })
        }

    @Test
    fun detectReportsDetectorAndPresence() =
        runTest {
            bridge.detectScript += modelDetect(5_000)
            val result = engine(DeviceCapability.Full).detect(frame)
            assertEquals(DetectorKind.Model, result.detector)
            assertEquals(0.95f, result.presence)
        }

    @Test
    fun analyzePassesKeepOrientationAndReportsTheDetector() =
        runTest {
            val corners = Quad(Point(200f, 200f), Point(3000f, 200f), Point(3000f, 2000f), Point(200f, 2000f))
            val result =
                engine(DeviceCapability.Full).analyze(
                    FakeScanPhoto(4000, 3000),
                    AnalyzeOptions(userCorners = corners, keepOrientation = true),
                )
            assertIs<AnalyzeResult.Ready>(result)
            assertTrue(bridge.calls.single().keepOrientation)
            assertEquals(DetectorKind.Classic, result.analysis.detector)
            assertEquals(0f, result.analysis.presence)
        }

    @Test
    fun slowModelOnSimplifiedDeviceSwitchesToClassicAfterFivePasses() =
        runTest {
            val engine = engine(DeviceCapability.Simplified)
            engine.loadDetector { byteArrayOf(1) }
            repeat(5) {
                bridge.detectScript += modelDetect(200_000)
                engine.detect(frame)
            }
            assertEquals(listOf(true, false), bridge.detectorEnabledCalls)
        }

    @Test
    fun fastModelOnSimplifiedDeviceKeepsTheModel() =
        runTest {
            val engine = engine(DeviceCapability.Simplified)
            engine.loadDetector { byteArrayOf(1) }
            repeat(8) {
                bridge.detectScript += modelDetect(60_000)
                engine.detect(frame)
            }
            assertEquals(listOf(true), bridge.detectorEnabledCalls)
        }

    @Test
    fun medianNotASingleSlowPassDecides() =
        runTest {
            val engine = engine(DeviceCapability.Simplified)
            engine.loadDetector { byteArrayOf(1) }
            // the first pass includes warm-up; the median of 400, 50, 60, 70, 80 ms is 70 ms
            listOf(400_000L, 50_000L, 60_000L, 70_000L, 80_000L).forEach {
                bridge.detectScript += modelDetect(it)
                engine.detect(frame)
            }
            assertEquals(listOf(true), bridge.detectorEnabledCalls)
        }

    @Test
    fun fullCapabilityNeverSwitchesAndClassicPassesAreNotTimed() =
        runTest {
            val full = engine(DeviceCapability.Full)
            full.loadDetector { byteArrayOf(1) }
            repeat(6) {
                bridge.detectScript += modelDetect(500_000)
                full.detect(frame)
            }
            assertEquals(listOf(true), bridge.detectorEnabledCalls)

            val simplified = engine(DeviceCapability.Simplified)
            simplified.loadDetector { byteArrayOf(1) }
            repeat(6) {
                bridge.detectScript += FakeNativeBridge.defaultDetect(frame, micros = 500_000)
                simplified.detect(frame)
            }
            assertEquals(listOf(true, true), bridge.detectorEnabledCalls)
        }

    @Test
    fun failedLoadLeavesTheClassicDetectorWithoutError() =
        runTest {
            bridge.loadDetectorResult = false
            val engine = engine(DeviceCapability.Simplified)
            assertFalse(engine.loadDetector { byteArrayOf(1) })
            val result = engine.detect(frame)
            assertEquals(DetectorKind.Classic, result.detector)
            assertEquals(Confidence.High, result.confidence)
            assertTrue(bridge.detectorEnabledCalls.isEmpty())
        }

    @Test
    fun cancellationInTheSourceIsNotSwallowed() =
        runTest {
            val engine = engine(DeviceCapability.Full)
            assertFailsWith<CancellationException> {
                engine.loadDetector { throw CancellationException("cancelled") }
            }
        }

    @Test
    fun loadDetectorHappensOncePerEngine() =
        runTest {
            val engine = engine(DeviceCapability.Full)
            assertTrue(engine.loadDetector { byteArrayOf(1) })
            assertTrue(engine.loadDetector { byteArrayOf(2) })
            assertEquals(1, bridge.loadedDetectors.size)
        }
}
