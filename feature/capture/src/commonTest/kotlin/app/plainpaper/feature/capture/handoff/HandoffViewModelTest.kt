package app.plainpaper.feature.capture.handoff

import app.cash.turbine.test
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.model.capture.SessionStatus
import app.plainpaper.core.domain.usecase.capture.FinishSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ObserveActiveSessionUseCase
import app.plainpaper.core.scanengine.DefaultScanEngine
import app.plainpaper.core.testing.FakeCapabilityProvider
import app.plainpaper.core.testing.FakeCaptureSessionRepository
import app.plainpaper.core.testing.FakeNativeBridge
import app.plainpaper.core.testing.FakePhotoDecoder
import app.plainpaper.core.testing.TestDispatcherProvider
import app.plainpaper.feature.capture.fakes.FakeBackgroundTask
import app.plainpaper.feature.capture.fakes.FakeImageFiles
import app.plainpaper.feature.capture.fakes.FakeThumbnailLoader
import app.plainpaper.feature.capture.processing.PageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HandoffViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val sessions = FakeCaptureSessionRepository()
    private val thumbnails = FakeThumbnailLoader()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val bridge = FakeNativeBridge()
    private val files = FakeImageFiles()

    private fun processor(): PageProcessor {
        val dispatchers = TestDispatcherProvider(dispatcher)
        return PageProcessor(
            engine = DefaultScanEngine(bridge, FakeCapabilityProvider(DeviceCapability.Full), dispatchers),
            decoder = FakePhotoDecoder(),
            files = files,
            sessions = sessions,
            background = FakeBackgroundTask(),
            dispatchers = dispatchers,
            scope = CoroutineScope(SupervisorJob() + dispatcher),
        )
    }

    private val pageProcessor by lazy { processor() }

    private fun viewModel(sessionId: String) =
        HandoffViewModel(
            sessionId = sessionId,
            observeSession = ObserveActiveSessionUseCase(sessions),
            finishSession = FinishSessionUseCase(sessions),
            processor = pageProcessor,
            thumbnails = thumbnails,
            dispatchers = TestDispatcherProvider(dispatcher),
        )

    @Test
    fun listsPagesInOrderAndTracksProcessing() =
        runTest {
            val session = sessions.start(SessionSource.Import)
            val a = sessions.addPage(session.id, "/a.jpg", PageTag.Document, PageSize.A4)
            val b = sessions.addPage(session.id, "/b.jpg", PageTag.Document, PageSize.A4)
            sessions.updatePage(session.id, a.copy(state = PageState.Ready, thumbnailPath = "/thumb-a.jpg"))
            val vm = viewModel(session.id)
            assertEquals(SessionStatus.HandedOff, sessions.state.value?.status)
            assertEquals(
                listOf(a.id, b.id),
                vm.uiState.value.pages
                    .map { it.id },
            )
            assertEquals(1, vm.uiState.value.processingRemaining)
            assertTrue("/thumb-a.jpg" in thumbnails.requested)
            sessions.updatePage(session.id, b.copy(state = PageState.Ready, thumbnailPath = "/thumb-b.jpg"))
            assertEquals(0, vm.uiState.value.processingRemaining)
            assertTrue("/thumb-b.jpg" in thumbnails.requested)
            assertEquals(
                PageState.Ready,
                vm.uiState.value.pages[1]
                    .state,
            )
        }

    @Test
    fun ignoresOtherSessionsAndNavigatesHome() =
        runTest {
            sessions.start(SessionSource.Camera)
            val vm = viewModel("someone-else")
            assertEquals(HandoffUiState(), vm.uiState.value)
            vm.effects.test {
                vm.onAction(HandoffAction.BackToHome)
                assertEquals(HandoffEffect.NavigateHome, awaitItem())
            }
        }

    private suspend fun processedSession(pages: Int): String {
        val session = sessions.start(SessionSource.Camera)
        repeat(pages) { i ->
            val page =
                sessions.addPage(
                    session.id,
                    "/fake/capture/${session.id}/o$i.jpg",
                    PageTag.Document,
                    PageSize.A4,
                )
            pageProcessor.enqueue(session.id, page.id)
        }
        return session.id
    }

    @Test
    fun pagesStartEnhancedAndTheCurrentPageHasAPreview() =
        runTest {
            val id = processedSession(2)
            val vm = viewModel(id)
            assertEquals(
                listOf(OutputStyle.ColorDocument, OutputStyle.ColorDocument),
                vm.uiState.value.pages
                    .map { it.style },
            )
            assertEquals(0, vm.uiState.value.current)
            assertTrue(
                vm.uiState.value.currentPage!!
                    .pagePath!! in thumbnails.requested,
                "preview of page 1 loaded",
            )
            vm.onAction(HandoffAction.SelectPage(1))
            assertEquals(1, vm.uiState.value.current)
            assertTrue(
                vm.uiState.value.currentPage!!
                    .pagePath!! in thumbnails.requested,
                "preview of page 2 loaded",
            )
            vm.onAction(HandoffAction.SelectPage(9))
            assertEquals(1, vm.uiState.value.current)
        }

    @Test
    fun choosingALookRestylesOnlyTheCurrentPage() =
        runTest {
            val id = processedSession(2)
            val vm = viewModel(id)
            vm.onAction(HandoffAction.ChooseLook(OutputStyle.Scan))
            assertEquals(
                listOf(OutputStyle.Scan, OutputStyle.ColorDocument),
                vm.uiState.value.pages
                    .map { it.style },
            )
            assertTrue(
                vm.uiState.value.pages
                    .all { it.state == PageState.Ready },
            )
            assertTrue(
                vm.uiState.value.pages[0]
                    .pagePath!!
                    .endsWith("-scan.jpg"),
            )
        }

    @Test
    fun applyToAllUsesTheCurrentPagesLook() =
        runTest {
            val id = processedSession(3)
            val vm = viewModel(id)
            vm.onAction(HandoffAction.ChooseLook(OutputStyle.Grayscale))
            vm.onAction(HandoffAction.ApplyLookToAll)
            assertEquals(
                List(3) { OutputStyle.Grayscale },
                vm.uiState.value.pages
                    .map { it.style },
            )
        }

    @Test
    fun rotateAndFlipTurnOnlyTheCurrentPage() =
        runTest {
            val id = processedSession(2)
            val vm = viewModel(id)
            vm.onAction(HandoffAction.SelectPage(1))
            vm.onAction(HandoffAction.RotatePage)
            vm.onAction(HandoffAction.FlipPage)
            val pages = sessions.state.value!!.pages
            assertEquals(Triple(0, false, 0), Triple(pages[0].rotation, pages[0].mirrored, pages[0].revision))
            assertEquals(Triple(270, true, 2), Triple(pages[1].rotation, pages[1].mirrored, pages[1].revision))
            assertEquals(PageState.Ready, pages[1].state)
            assertEquals(270 to true, files.transforms[pages[1].pagePath])
            assertTrue(pages[1].pagePath!!.contains("-r2-"))
        }
}
