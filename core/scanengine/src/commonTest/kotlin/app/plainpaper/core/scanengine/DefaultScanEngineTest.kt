package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.scanengine.bridge.NativeStatus
import app.plainpaper.core.scanengine.model.AnalysisFlag
import app.plainpaper.core.scanengine.model.AnalyzeOptions
import app.plainpaper.core.scanengine.model.Outcome
import app.plainpaper.core.scanengine.model.Point
import app.plainpaper.core.scanengine.model.Quad
import app.plainpaper.core.scanengine.model.RenderOptions
import app.plainpaper.core.testing.FakeCapabilityProvider
import app.plainpaper.core.testing.FakeNativeBridge
import app.plainpaper.core.testing.FakeScanPhoto
import app.plainpaper.core.testing.TestDispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultScanEngineTest {
    private val bridge = FakeNativeBridge()
    private val capability = FakeCapabilityProvider(DeviceCapability.Full)
    private val photo = FakeScanPhoto(4000, 3000)

    private fun engine(dispatchers: DispatcherProvider) = DefaultScanEngine(bridge, capability, dispatchers)

    /** Counts dispatches so the test can prove work moved to the default dispatcher (K1). */
    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatches = 0

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            dispatches++
            // Run inline: the scheduler-backed delegate only accepts dispatches from `yield`.
            block.run()
        }
    }

    @Test
    fun analyzeRunsOnTheDefaultDispatcher() =
        runTest {
            val recording = RecordingDispatcher()
            val dispatchers =
                object : DispatcherProvider {
                    override val main = UnconfinedTestDispatcher(testScheduler)
                    override val io = main
                    override val default = recording
                }
            val result = engine(dispatchers).analyze(photo)
            assertIs<AnalyzeResult.Ready>(result)
            assertTrue(recording.dispatches > 0, "work must be dispatched to default")
        }

    @Test
    fun cancellationSetsTheNativeFlagAndDeliversNoResult() =
        runTest {
            val engine = engine(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
            var job: Job? = null
            var result: AnalyzeResult? = null
            bridge.onAnalyze = { job?.cancel() }
            job = launch { result = engine.analyze(photo) }
            job.join()
            assertTrue(job.isCancelled)
            assertNull(result)
            assertTrue(bridge.flags.single().isSet, "cancel flag set synchronously on cancellation")
            assertTrue(bridge.flags.single().destroyed)
            assertTrue(bridge.sessions.isEmpty())
        }

    @Test
    fun outOfMemoryRetriesOnceAtHalfResolutionSimplified() =
        runTest {
            bridge.analyzeScript += FakeNativeBridge.Script.Fail(NativeStatus.OutOfMemory)
            bridge.analyzeScript += FakeNativeBridge.Script.Succeed()
            val result = engine(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))).analyze(photo)
            val ready = assertIs<AnalyzeResult.Ready>(result)
            assertEquals(2, bridge.calls.size)
            assertEquals(DeviceCapability.Full, bridge.calls[0].capability)
            assertEquals(2000, bridge.calls[0].workingLongEdge)
            assertEquals(DeviceCapability.Simplified, bridge.calls[1].capability)
            assertEquals(1000, bridge.calls[1].workingLongEdge)
            assertFalse(bridge.calls[1].allowFlatten)
            assertTrue(AnalysisFlag.RetriedAtLowerResolution in ready.analysis.flags)
            assertEquals(DeviceCapability.Simplified, ready.analysis.capabilityUsed)
            assertEquals(1000, ready.analysis.workingLongEdgeUsed)
        }

    @Test
    fun outOfMemoryTwiceFails() =
        runTest {
            bridge.analyzeScript += FakeNativeBridge.Script.Fail(NativeStatus.OutOfMemory)
            bridge.analyzeScript += FakeNativeBridge.Script.Fail(NativeStatus.OutOfMemory)
            val result = engine(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))).analyze(photo)
            assertEquals(AnalyzeResult.Failed(Outcome.OutOfMemory), result)
            assertEquals(2, bridge.calls.size)
        }

    @Test
    fun tooDarkStillDeliversAnAnalysis() =
        runTest {
            bridge.analyzeScript += FakeNativeBridge.Script.Succeed(status = NativeStatus.TooDark)
            val result = engine(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))).analyze(photo)
            val ready = assertIs<AnalyzeResult.Ready>(result)
            assertEquals(Outcome.TooDark, ready.analysis.outcome)
        }

    @Test
    fun invalidUserCornersAreRejectedBeforeNative() =
        runTest {
            val bad = Quad(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f), Point(0f, 10f)) // 0.001% of the photo
            val result =
                engine(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
                    .analyze(photo, AnalyzeOptions(userCorners = bad))
            assertEquals(AnalyzeResult.Failed(Outcome.InvalidInput("corners")), result)
            assertTrue(bridge.calls.isEmpty())
        }

    @Test
    fun validUserCornersArePassedThrough() =
        runTest {
            val corners = Quad(Point(100f, 100f), Point(3900f, 120f), Point(3880f, 2900f), Point(120f, 2880f))
            engine(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
                .analyze(photo, AnalyzeOptions(userCorners = corners))
            assertEquals(corners, bridge.calls.single().userCorners)
        }

    @Test
    fun renderUsesPageSizePixelsAndSessionGeometry() =
        runTest {
            val engine = engine(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
            val analysis = assertIs<AnalyzeResult.Ready>(engine.analyze(photo)).analysis
            val result = engine.render(analysis, RenderOptions(OutputStyle.Scan, PageSize.A4))
            val ready = assertIs<RenderResult.Ready>(result)
            assertEquals(2480, ready.output.width)
            assertEquals(3508, ready.output.height)
            assertEquals(1, ready.output.channels)
            val natural =
                assertIs<RenderResult.Ready>(
                    engine.render(analysis, RenderOptions(OutputStyle.Original, PageSize.Natural)),
                )
            assertEquals(3508, natural.output.height)
            assertEquals(2480, natural.output.width) // 3508 * 0.707 = 2480.2 -> 2480
        }

    @Test
    fun renderAfterCloseThrowsAndCloseIsIdempotent() =
        runTest {
            val engine = engine(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
            val analysis = assertIs<AnalyzeResult.Ready>(engine.analyze(photo)).analysis
            analysis.close()
            analysis.close()
            assertEquals(1, bridge.sessions.single().destroyCount)
            assertFailsWith<IllegalStateException> {
                engine.render(analysis, RenderOptions(OutputStyle.Scan, PageSize.A4))
            }
        }

    @Test
    fun renderFailureIsMapped() =
        runTest {
            val engine = engine(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
            val analysis = assertIs<AnalyzeResult.Ready>(engine.analyze(photo)).analysis
            bridge.renderScript += FakeNativeBridge.Script.Fail(NativeStatus.Internal, "boom")
            val result = engine.render(analysis, RenderOptions(OutputStyle.Scan, PageSize.A4))
            assertEquals(RenderResult.Failed(Outcome.Internal("boom")), result)
        }

    @Test
    fun apiVersionMismatchFailsWithoutCallingNative() =
        runTest {
            bridge.apiVersion = 99
            val result = engine(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))).analyze(photo)
            assertEquals(AnalyzeResult.Failed(Outcome.Internal(DefaultScanEngine.API_VERSION_MISMATCH)), result)
            assertTrue(bridge.calls.isEmpty())
        }

    @Test
    fun capabilityOverrideAndEngineCapability() =
        runTest {
            capability.capability = DeviceCapability.Simplified
            val engine = engine(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
            assertEquals(DeviceCapability.Simplified, engine.capability)
            engine.analyze(photo)
            assertEquals(1500, bridge.calls[0].workingLongEdge)
            assertFalse(bridge.calls[0].allowFlatten)
            engine.analyze(photo, AnalyzeOptions(capability = DeviceCapability.Full, workingLongEdge = 2600))
            assertEquals(DeviceCapability.Full, bridge.calls[1].capability)
            assertEquals(2600, bridge.calls[1].workingLongEdge)
            assertTrue(bridge.calls[1].allowFlatten)
        }
}
