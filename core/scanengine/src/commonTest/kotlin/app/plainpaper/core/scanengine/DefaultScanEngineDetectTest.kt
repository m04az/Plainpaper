package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.bridge.NativeStatus
import app.plainpaper.core.scanengine.model.Confidence
import app.plainpaper.core.scanengine.model.Point
import app.plainpaper.core.scanengine.model.Quad
import app.plainpaper.core.testing.FakeCapabilityProvider
import app.plainpaper.core.testing.FakeNativeBridge
import app.plainpaper.core.testing.TestDispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultScanEngineDetectTest {
    private val bridge = FakeNativeBridge()
    private val engine =
        DefaultScanEngine(bridge, FakeCapabilityProvider(DeviceCapability.Full), TestDispatcherProvider())
    private val frame = ByteArrayPreviewFrame(640, 480, 640 * 4, PixelFormat.BGRA, 1L, ByteArray(640 * 480 * 4))

    @Test
    fun mapsCornersAndMetrics() =
        runTest {
            val quad = Quad(Point(10f, 10f), Point(600f, 12f), Point(590f, 470f), Point(20f, 460f))
            bridge.detectScript +=
                FakeNativeBridge
                    .defaultDetect(
                        frame,
                    ).copy(corners = quad, fillFraction = 0.83f, meanLuma = 170f, micros = 9_000)
            val result = engine.detect(frame)
            assertEquals(quad, result.corners)
            assertEquals(Confidence.High, result.confidence)
            assertEquals(0.83f, result.fillFraction)
            assertEquals(170f, result.meanLuma)
            assertEquals(9_000L, result.micros)
            assertEquals<List<PreviewFrame>>(listOf(frame), bridge.detectCalls)
        }

    @Test
    fun notFoundHasNoCorners() =
        runTest {
            bridge.detectScript += FakeNativeBridge.defaultDetect(frame, confidence = Confidence.NotFound)
            val result = engine.detect(frame)
            assertNull(result.corners)
            assertEquals(Confidence.NotFound, result.confidence)
        }

    @Test
    fun internalFailureReadsAsNotFoundWithoutThrowing() =
        runTest {
            bridge.detectScript +=
                FakeNativeBridge
                    .defaultDetect(
                        frame,
                    ).copy(status = NativeStatus.Internal, detail = "boom", meanLuma = 33f)
            val result = engine.detect(frame)
            assertNull(result.corners)
            assertEquals(Confidence.NotFound, result.confidence)
            assertEquals(33f, result.meanLuma)
        }

    @Test
    fun cancellationSetsTheFlagAndDeliversNothing() =
        runTest {
            var delivered = false
            var cancelled = false
            val job =
                launch {
                    bridge.onAnalyze = null
                    try {
                        bridge.detectScript +=
                            FakeNativeBridge.defaultDetect(frame).copy(status = NativeStatus.Cancelled)
                        engine.detect(frame)
                        delivered = true
                    } catch (_: CancellationException) {
                        cancelled = true
                    }
                }
            job.join()
            assertTrue(cancelled)
            assertTrue(!delivered)
            assertTrue(bridge.flags.single().destroyed)
        }
}
