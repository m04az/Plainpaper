package app.plainpaper.feature.capture.handoff

import app.cash.turbine.test
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.model.capture.SessionStatus
import app.plainpaper.core.domain.usecase.capture.FinishSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ObserveActiveSessionUseCase
import app.plainpaper.core.testing.FakeCaptureSessionRepository
import app.plainpaper.core.testing.TestDispatcherProvider
import app.plainpaper.feature.capture.fakes.FakeThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun viewModel(sessionId: String) =
        HandoffViewModel(
            sessionId = sessionId,
            observeSession = ObserveActiveSessionUseCase(sessions),
            finishSession = FinishSessionUseCase(sessions),
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
            assertEquals(listOf("/thumb-a.jpg"), thumbnails.requested)
            sessions.updatePage(session.id, b.copy(state = PageState.Ready, thumbnailPath = "/thumb-b.jpg"))
            assertEquals(0, vm.uiState.value.processingRemaining)
            assertEquals(listOf("/thumb-a.jpg", "/thumb-b.jpg"), thumbnails.requested)
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
}
