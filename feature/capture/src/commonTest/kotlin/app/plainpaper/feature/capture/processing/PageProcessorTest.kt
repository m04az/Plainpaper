package app.plainpaper.feature.capture.processing

import app.plainpaper.core.domain.model.Confidence
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.AttentionReason
import app.plainpaper.core.domain.model.capture.CapturedPage
import app.plainpaper.core.domain.model.capture.PageFailure
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.scanengine.DefaultScanEngine
import app.plainpaper.core.scanengine.bridge.NativeStatus
import app.plainpaper.core.testing.FakeCapabilityProvider
import app.plainpaper.core.testing.FakeCaptureSessionRepository
import app.plainpaper.core.testing.FakeNativeBridge
import app.plainpaper.core.testing.FakePhotoDecoder
import app.plainpaper.core.testing.TestDispatcherProvider
import app.plainpaper.feature.capture.fakes.FakeBackgroundTask
import app.plainpaper.feature.capture.fakes.FakeImageFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PageProcessorTest {
    private val bridge = FakeNativeBridge()
    private val capability = FakeCapabilityProvider(DeviceCapability.Full)
    private val decoder = FakePhotoDecoder()
    private val files = FakeImageFiles()
    private val sessions = FakeCaptureSessionRepository()
    private val background = FakeBackgroundTask()

    private fun TestScope.processor(): PageProcessor {
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
        return PageProcessor(
            engine = DefaultScanEngine(bridge, capability, dispatchers),
            decoder = decoder,
            files = files,
            sessions = sessions,
            background = background,
            dispatchers = dispatchers,
            scope = scope,
        )
    }

    private suspend fun page(name: String = "o"): Pair<String, CapturedPage> {
        val session = sessions.state.value ?: sessions.start(SessionSource.Camera)
        val page = sessions.addPage(session.id, "/fake/capture/${session.id}/$name.jpg", PageTag.Document, PageSize.A4)
        return session.id to page
    }

    private fun stored(pageId: String): CapturedPage = assertNotNull(sessions.state.value?.page(pageId))

    @Test
    fun readyPageGetsFilesCornersAndThumbnail() =
        runTest {
            val processor = processor()
            val (sessionId, page) = page()
            processor.enqueue(sessionId, page.id)
            advanceUntilIdle()
            val result = stored(page.id)
            assertEquals(PageState.Ready, result.state)
            assertEquals("/fake/capture/$sessionId/page-000.jpg", result.pagePath)
            assertEquals("/fake/capture/$sessionId/thumb-000.jpg", result.thumbnailPath)
            assertEquals(Confidence.High, result.confidence)
            assertNotNull(result.corners)
            assertEquals(listOf(result.pagePath), files.jpegs)
            assertEquals(1, background.keepAliveCount)
            assertTrue(processor.inFlight.value.isEmpty())
        }

    @Test
    fun attentionStatesFollowConfidenceAndDarkness() =
        runTest {
            val processor = processor()
            val (sessionId, notFound) = page("a")
            val (_, low) = page("b")
            val (_, dark) = page("c")
            bridge.analyzeScript +=
                FakeNativeBridge.Script.Succeed(info = FakeNativeBridge.defaultInfo(confidence = Confidence.NotFound))
            bridge.analyzeScript +=
                FakeNativeBridge.Script.Succeed(info = FakeNativeBridge.defaultInfo(confidence = Confidence.Low))
            bridge.analyzeScript += FakeNativeBridge.Script.Succeed(status = NativeStatus.TooDark)
            decoder.block = true
            listOf(notFound, low, dark).forEach { processor.enqueue(sessionId, it.id) }
            advanceUntilIdle()
            // Release in order so the scripted results map to the intended pages.
            decoder.release(notFound.originalPath)
            advanceUntilIdle()
            decoder.release(low.originalPath)
            advanceUntilIdle()
            decoder.release(dark.originalPath)
            advanceUntilIdle()
            assertEquals(PageState.NeedsAttention(AttentionReason.NoPageFound), stored(notFound.id).state)
            assertEquals(PageState.NeedsAttention(AttentionReason.LowConfidence), stored(low.id).state)
            assertEquals(PageState.NeedsAttention(AttentionReason.TooDark), stored(dark.id).state)
            assertNotNull(stored(dark.id).pagePath)
        }

    @Test
    fun failuresKeepTheOriginalAndReportTheReason() =
        runTest {
            val processor = processor()
            val (sessionId, unreadable) = page("unreadable")
            val (_, oom) = page("oom")
            val (_, badWrite) = page("write")
            bridge.analyzeScript += FakeNativeBridge.Script.Fail(NativeStatus.OutOfMemory)
            bridge.analyzeScript += FakeNativeBridge.Script.Fail(NativeStatus.OutOfMemory)
            processor.enqueue(sessionId, unreadable.id)
            processor.enqueue(sessionId, oom.id)
            advanceUntilIdle()
            assertEquals(PageState.Failed(PageFailure.Unreadable), stored(unreadable.id).state)
            assertEquals(PageState.Failed(PageFailure.OutOfMemory), stored(oom.id).state)
            files.failWrites = true
            processor.enqueue(sessionId, badWrite.id)
            advanceUntilIdle()
            assertEquals(PageState.Failed(PageFailure.Internal), stored(badWrite.id).state)
            assertNull(stored(badWrite.id).pagePath)
        }

    @Test
    fun cancellingAPageMarksItCancelled() =
        runTest {
            val processor = processor()
            val (sessionId, page) = page()
            decoder.block = true
            processor.enqueue(sessionId, page.id)
            advanceUntilIdle()
            assertEquals(setOf(page.id), processor.inFlight.value)
            processor.cancel(sessionId, page.id)
            advanceUntilIdle()
            assertEquals(PageState.Failed(PageFailure.Cancelled), stored(page.id).state)
            assertTrue(processor.inFlight.value.isEmpty())
        }

    @Test
    fun cancellingTheSessionStopsQueuedWork() =
        runTest {
            val processor = processor()
            val (sessionId, first) = page("a")
            val (_, second) = page("b")
            val (_, third) = page("c")
            decoder.block = true
            listOf(first, second, third).forEach { processor.enqueue(sessionId, it.id) }
            advanceUntilIdle()
            assertEquals(2, processor.inFlight.value.size)
            processor.cancel(sessionId)
            sessions.discard(sessionId)
            decoder.releaseAll()
            advanceUntilIdle()
            assertTrue(processor.inFlight.value.isEmpty())
            assertEquals(2, decoder.decoded.size, "the third page never started")
        }

    @Test
    fun concurrencyDependsOnCapability() =
        runTest {
            capability.capability = DeviceCapability.Simplified
            val processor = processor()
            assertEquals(1, processor.concurrency)
            val (sessionId, a) = page("a")
            val (_, b) = page("b")
            decoder.block = true
            processor.enqueue(sessionId, a.id)
            processor.enqueue(sessionId, b.id)
            advanceUntilIdle()
            assertEquals(setOf(a.id), processor.inFlight.value)
            decoder.release(a.originalPath)
            advanceUntilIdle()
            assertEquals(setOf(b.id), processor.inFlight.value)
            decoder.release(b.originalPath)
            advanceUntilIdle()
            assertEquals(PageState.Ready, stored(b.id).state)
        }

    @Test
    fun resumeRequeuesPendingPagesOnly() =
        runTest {
            val processor = processor()
            val (sessionId, pending) = page("a")
            val (_, done) = page("b")
            sessions.updatePage(sessionId, done.copy(state = PageState.Ready, pagePath = "/x.jpg"))
            processor.resume()
            advanceUntilIdle()
            assertEquals(PageState.Ready, stored(pending.id).state)
            assertEquals(listOf(pending.originalPath), decoder.decoded)
            assertIs<PageState.Ready>(stored(done.id).state)
        }

    @Test
    fun aDiscardedSessionIsIgnoredWithoutErrors() =
        runTest {
            val processor = processor()
            val (sessionId, page) = page()
            decoder.block = true
            processor.enqueue(sessionId, page.id)
            advanceUntilIdle()
            sessions.discard(sessionId)
            decoder.releaseAll()
            advanceUntilIdle()
            assertNull(sessions.state.value)
            assertTrue(processor.inFlight.value.isEmpty())
            (processor.javaClassName()).let { }
        }

    private fun PageProcessor.javaClassName(): String = this::class.simpleName.orEmpty()

    @Test
    fun scopeCancellationStopsWorkers() =
        runTest {
            val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
            val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
            val processor =
                PageProcessor(
                    DefaultScanEngine(bridge, capability, dispatchers),
                    decoder,
                    files,
                    sessions,
                    background,
                    dispatchers,
                    scope,
                )
            val (sessionId, page) = page()
            decoder.block = true
            processor.enqueue(sessionId, page.id)
            advanceUntilIdle()
            scope.cancel()
            advanceUntilIdle()
            assertEquals(PageState.Processing, stored(page.id).state)
        }
}
