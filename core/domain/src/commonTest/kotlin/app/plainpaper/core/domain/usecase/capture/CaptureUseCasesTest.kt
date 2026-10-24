package app.plainpaper.core.domain.usecase.capture

import app.cash.turbine.test
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.CaptureSession
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.model.capture.SessionStatus
import app.plainpaper.core.testing.FakeCaptureSessionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptureUseCasesTest {
    private val repository = FakeCaptureSessionRepository()

    @Test
    fun startAddFinishRoundTrip() =
        runTest {
            val session = StartSessionUseCase(repository)(SessionSource.Camera)
            assertEquals(SessionSource.Camera, session.source)
            val page = AddCapturedPageUseCase(repository)(session.id, "/o.jpg", PageTag.Receipt, PageSize.Natural)
            assertEquals(0, page.position)
            assertEquals(PageTag.Receipt, page.tag)
            FinishSessionUseCase(repository)(session.id)
            assertEquals(SessionStatus.HandedOff, repository.state.value?.status)
        }

    @Test
    fun observeEmitsCurrentValueAndChangesWithoutDuplicates() =
        runTest {
            ObserveActiveSessionUseCase(repository)().test {
                assertNull(awaitItem())
                val session = repository.start(SessionSource.Import)
                assertEquals(session, awaitItem())
                repository.state.value = session
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun discardForwardsAndClears() =
        runTest {
            val session = repository.start(SessionSource.Camera)
            DiscardSessionUseCase(repository)(session.id)
            assertEquals(listOf(session.id), repository.discarded)
            assertNull(repository.state.value)
        }

    @Test
    fun recoverReturnsOnlySessionsWithPages() =
        runTest {
            assertNull(RecoverSessionUseCase(repository)())
            repository.recoverableSession = CaptureSession("old", SessionSource.Camera, 1L)
            assertNull(RecoverSessionUseCase(repository)())
            val withPage = repository.start(SessionSource.Camera)
            repository.addPage(withPage.id, "/o.jpg", PageTag.Document, PageSize.A4)
            repository.recoverableSession = repository.state.value
            assertEquals(withPage.id, RecoverSessionUseCase(repository)()?.id)
        }

    @Test
    fun removePageForwardsAndDropsThePage() =
        runTest {
            val session = repository.start(SessionSource.Camera)
            val a = repository.addPage(session.id, "/a.jpg", PageTag.Document, PageSize.A4)
            val b = repository.addPage(session.id, "/b.jpg", PageTag.Document, PageSize.A4)
            RemovePageUseCase(repository)(session.id, a.id)
            assertEquals(listOf(a.id), repository.removedPages)
            assertEquals(
                listOf(b.id),
                repository.state.value
                    ?.pages
                    ?.map { it.id },
            )
        }
}
