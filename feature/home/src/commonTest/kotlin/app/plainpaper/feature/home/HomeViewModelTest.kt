package app.plainpaper.feature.home

import app.cash.turbine.test
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.usecase.capture.DiscardSessionUseCase
import app.plainpaper.core.domain.usecase.capture.RecoverSessionUseCase
import app.plainpaper.core.testing.FakeCaptureSessionRepository
import app.plainpaper.core.testing.TestDispatcherProvider
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val sessions = FakeCaptureSessionRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        HomeViewModel(
            RecoverSessionUseCase(sessions),
            DiscardSessionUseCase(sessions),
            TestDispatcherProvider(dispatcher),
        )

    private suspend fun leftoverSession(pages: Int): String {
        val session = sessions.start(SessionSource.Camera)
        repeat(pages) { sessions.addPage(session.id, "/o$it.jpg", PageTag.Document, PageSize.A4) }
        sessions.recoverableSession = sessions.state.value
        return session.id
    }

    @Test
    fun initialStateIsEmptyWithoutRecovery() {
        assertEquals(HomeUiState(isEmpty = true, recoverable = null), viewModel().uiState.value)
    }

    @Test
    fun scanAndImportClicksEmitEffects() =
        runTest {
            val viewModel = viewModel()
            viewModel.effects.test {
                viewModel.onAction(HomeAction.ScanClicked)
                assertEquals(HomeEffect.NavigateToScan, awaitItem())
                viewModel.onAction(HomeAction.ImportClicked)
                assertEquals(HomeEffect.NavigateToImport, awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun recoverableSessionShowsTheCardAndContinueNavigates() =
        runTest {
            val id = leftoverSession(pages = 3)
            val viewModel = viewModel()
            assertEquals(RecoverableSession(id, 3), viewModel.uiState.value.recoverable)
            viewModel.effects.test {
                viewModel.onAction(HomeAction.ContinueRecovery)
                assertEquals(HomeEffect.NavigateToHandoff(id), awaitItem())
            }
        }

    @Test
    fun discardRemovesTheCardAndTheSession() =
        runTest {
            val id = leftoverSession(pages = 2)
            val viewModel = viewModel()
            viewModel.onAction(HomeAction.DiscardRecovery)
            assertNull(viewModel.uiState.value.recoverable)
            assertEquals(listOf(id), sessions.discarded)
            viewModel.onAction(HomeAction.ScreenStarted)
            assertNull(viewModel.uiState.value.recoverable)
        }

    @Test
    fun screenStartRechecksForARecoverableSession() =
        runTest {
            val viewModel = viewModel()
            assertNull(viewModel.uiState.value.recoverable)
            val id = leftoverSession(pages = 1)
            viewModel.onAction(HomeAction.ScreenStarted)
            assertEquals(RecoverableSession(id, 1), viewModel.uiState.value.recoverable)
        }
}
