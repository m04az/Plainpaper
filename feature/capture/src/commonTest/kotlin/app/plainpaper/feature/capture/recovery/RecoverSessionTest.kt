package app.plainpaper.feature.capture.recovery

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.usecase.capture.DiscardSessionUseCase
import app.plainpaper.core.domain.usecase.capture.RecoverSessionUseCase
import app.plainpaper.core.scanengine.DefaultScanEngine
import app.plainpaper.core.testing.FakeCapabilityProvider
import app.plainpaper.core.testing.FakeCaptureSessionRepository
import app.plainpaper.core.testing.FakeNativeBridge
import app.plainpaper.core.testing.FakePhotoDecoder
import app.plainpaper.core.testing.TestDispatcherProvider
import app.plainpaper.feature.capture.fakes.FakeBackgroundTask
import app.plainpaper.feature.capture.fakes.FakeImageFiles
import app.plainpaper.feature.capture.processing.PageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Spec US3: a session left by a previous run is offered once and its pending pages resume. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecoverSessionTest {
    private val sessions = FakeCaptureSessionRepository()
    private val decoder = FakePhotoDecoder()

    @Test
    fun recoveredSessionResumesOnlyPendingPages() =
        runTest {
            // Leftover from a previous process: one page done, one pending, one that was mid-flight.
            val session = sessions.start(SessionSource.Camera)
            val done = sessions.addPage(session.id, "/done.jpg", PageTag.Document, PageSize.A4)
            sessions.addPage(session.id, "/pending.jpg", PageTag.Document, PageSize.A4)
            sessions.updatePage(session.id, done.copy(state = PageState.Ready, pagePath = "/p.jpg"))
            sessions.recoverableSession = sessions.state.value
            sessions.state.value = null // the app restarted

            val recovered = assertNotNull(RecoverSessionUseCase(sessions)())
            assertEquals(session.id, recovered.id)
            assertEquals(2, recovered.pageCount)

            val dispatcher = StandardTestDispatcher(testScheduler)
            val dispatchers = TestDispatcherProvider(dispatcher)
            val processor =
                PageProcessor(
                    engine =
                        DefaultScanEngine(
                            FakeNativeBridge(),
                            FakeCapabilityProvider(DeviceCapability.Full),
                            dispatchers,
                        ),
                    decoder = decoder,
                    files = FakeImageFiles(),
                    sessions = sessions,
                    background = FakeBackgroundTask(),
                    dispatchers = dispatchers,
                    scope = CoroutineScope(SupervisorJob() + dispatcher),
                )
            processor.resume()
            advanceUntilIdle()
            assertEquals(listOf("/pending.jpg"), decoder.decoded)
            assertEquals(
                PageState.Ready,
                sessions.state.value
                    ?.pages
                    ?.get(1)
                    ?.state,
            )
        }

    @Test
    fun discardingTheRecoveredSessionRemovesIt() =
        runTest {
            val session = sessions.start(SessionSource.Import)
            sessions.addPage(session.id, "/a.jpg", PageTag.Document, PageSize.A4)
            sessions.recoverableSession = sessions.state.value
            DiscardSessionUseCase(sessions)(session.id)
            assertNull(RecoverSessionUseCase(sessions)())
            assertEquals(listOf(session.id), sessions.discarded)
        }
}
