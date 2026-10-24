package app.plainpaper.feature.capture.import

import app.cash.turbine.test
import app.plainpaper.core.capture.picker.PickedPhoto
import app.plainpaper.core.domain.model.Confidence
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.usecase.capture.AddCapturedPageUseCase
import app.plainpaper.core.domain.usecase.capture.DiscardSessionUseCase
import app.plainpaper.core.domain.usecase.capture.EvaluateImportLimitUseCase
import app.plainpaper.core.domain.usecase.capture.ObserveActiveSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ResolveDefaultPageSizeUseCase
import app.plainpaper.core.domain.usecase.capture.SessionDirectoryUseCase
import app.plainpaper.core.domain.usecase.capture.StartSessionUseCase
import app.plainpaper.core.scanengine.DefaultScanEngine
import app.plainpaper.core.scanengine.bridge.NativeStatus
import app.plainpaper.core.testing.FakeCapabilityProvider
import app.plainpaper.core.testing.FakeCaptureSessionRepository
import app.plainpaper.core.testing.FakeNativeBridge
import app.plainpaper.core.testing.FakePhotoDecoder
import app.plainpaper.core.testing.FakeProStatusRepository
import app.plainpaper.core.testing.FakeRegionProvider
import app.plainpaper.core.testing.TestDispatcherProvider
import app.plainpaper.feature.capture.fakes.FakeBackgroundTask
import app.plainpaper.feature.capture.fakes.FakeImageFiles
import app.plainpaper.feature.capture.fakes.FakeThumbnailLoader
import app.plainpaper.feature.capture.processing.PageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    private val sessions = FakeCaptureSessionRepository()
    private val pro = FakeProStatusRepository()
    private val files = FakeImageFiles()
    private val thumbnails = FakeThumbnailLoader()
    private val decoder = FakePhotoDecoder()
    private val bridge = FakeNativeBridge()
    private var clock = 0L

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @BeforeTest
    fun setUp() {
        clock = 0L
    }

    private fun TestScope.viewModel(): ImportViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val dispatchers = TestDispatcherProvider(dispatcher)
        val processor =
            PageProcessor(
                engine = DefaultScanEngine(bridge, FakeCapabilityProvider(DeviceCapability.Full), dispatchers),
                decoder = decoder,
                files = files,
                sessions = sessions,
                background = FakeBackgroundTask(),
                dispatchers = dispatchers,
                scope = CoroutineScope(SupervisorJob() + dispatcher),
            )
        return ImportViewModel(
            evaluateLimit = EvaluateImportLimitUseCase(pro),
            startSession = StartSessionUseCase(sessions),
            addPage = AddCapturedPageUseCase(sessions),
            discardSession = DiscardSessionUseCase(sessions),
            observeSession = ObserveActiveSessionUseCase(sessions),
            sessionDirectory = SessionDirectoryUseCase(sessions),
            resolvePageSize = ResolveDefaultPageSizeUseCase(FakeRegionProvider("US")),
            files = files,
            thumbnails = thumbnails,
            processor = processor,
            dispatchers = dispatchers,
            now = { clock },
        )
    }

    private fun photos(count: Int) = List(count) { PickedPhoto("content://photo/$it") }

    @Test
    fun opensThePickerAndGoesBackWhenNothingIsPicked() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                assertEquals(ImportEffect.OpenPicker(PICKER_MAX_ITEMS), awaitItem())
                vm.onAction(ImportAction.Picked(emptyList()))
                assertEquals(ImportEffect.NavigateBack, awaitItem())
            }
        }

    @Test
    fun freeUsersSeeTheLimitSheetAndCanImportTheFirstTen() =
        runTest {
            val vm = viewModel()
            vm.onAction(ImportAction.Picked(photos(12)))
            advanceUntilIdle()
            assertEquals(ImportPhase.LimitSheet, vm.uiState.value.phase)
            assertEquals(12, vm.uiState.value.pickedCount)
            assertEquals(10, vm.uiState.value.limit)
            vm.onAction(ImportAction.ImportFirst)
            advanceUntilIdle()
            val session = assertNotNull(sessions.state.value)
            assertEquals(SessionSource.Import, session.source)
            assertEquals(10, session.pageCount)
            assertEquals((0 until 10).map { "content://photo/$it" }, files.copies.map { it.first })
            assertTrue(session.pages.all { it.tag == PageTag.Document })
            assertEquals(ImportPhase.Finished, vm.uiState.value.phase)
            assertEquals(10, vm.uiState.value.doneCount)
        }

    @Test
    fun proUsersConvertEverythingWithoutASheet() =
        runTest {
            pro.state.value = pro.state.value.copy(isPro = true)
            val vm = viewModel()
            vm.onAction(ImportAction.Picked(photos(12)))
            advanceUntilIdle()
            assertEquals(12, sessions.state.value?.pageCount)
            assertEquals(ImportPhase.Finished, vm.uiState.value.phase)
        }

    @Test
    fun progressTilesEstimateAndSkippedItems() =
        runTest {
            val vm = viewModel()
            decoder.block = true
            val picked = photos(3) + PickedPhoto("content://unreadable/x") + PickedPhoto("content://photo/nopage")
            bridge.analyzeScript += FakeNativeBridge.Script.Succeed()
            bridge.analyzeScript += FakeNativeBridge.Script.Succeed()
            bridge.analyzeScript += FakeNativeBridge.Script.Succeed()
            bridge.analyzeScript +=
                FakeNativeBridge.Script.Succeed(info = FakeNativeBridge.defaultInfo(confidence = Confidence.NotFound))
            vm.onAction(ImportAction.Picked(picked))
            advanceUntilIdle()
            val state = vm.uiState.value
            assertEquals(ImportPhase.Converting, state.phase)
            assertEquals(5, state.total)
            assertEquals(ImportItemState.Skipped, state.items[3].state)
            assertEquals(2, state.items.count { it.state == ImportItemState.Converting })
            assertEquals(8, state.estimatedSecondsLeft) // 4 remaining × 2 s default
            val session = assertNotNull(sessions.state.value)
            clock = 1_000
            decoder.release(session.pages[0].originalPath)
            advanceUntilIdle()
            assertIs<ImportItemState.Done>(
                vm.uiState.value.items[0]
                    .state,
            )
            assertEquals(1, vm.uiState.value.doneCount)
            assertEquals(3, vm.uiState.value.estimatedSecondsLeft) // 3 remaining × 1 s
            assertEquals(listOf("/fake/capture/${session.id}/thumb-000-colordocument.jpg"), thumbnails.requested)
            decoder.block = false
            decoder.releaseAll()
            advanceUntilIdle()
            assertEquals(ImportPhase.Finished, vm.uiState.value.phase)
            assertEquals(4, vm.uiState.value.doneCount)
            assertEquals(1, vm.uiState.value.skippedCount)
            assertIs<ImportItemState.Done>(
                vm.uiState.value.items[4]
                    .state,
            ) // no page found still becomes a page
        }

    @Test
    fun cancelOffersKeepOrDiscard() =
        runTest {
            val vm = viewModel()
            decoder.block = true
            vm.onAction(ImportAction.Picked(photos(3)))
            advanceUntilIdle()
            val session = assertNotNull(sessions.state.value)
            decoder.release(session.pages[0].originalPath)
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.doneCount)
            vm.effects.test {
                assertEquals(ImportEffect.OpenPicker(PICKER_MAX_ITEMS), awaitItem())
                vm.onAction(ImportAction.Cancel)
                advanceUntilIdle()
                assertEquals(ImportPhase.CancelChoice, vm.uiState.value.phase)
                vm.onAction(ImportAction.KeepConverted)
                assertEquals(ImportEffect.NavigateToHandoff(session.id), awaitItem())
                vm.onAction(ImportAction.DiscardConverted)
                advanceUntilIdle()
                assertEquals(ImportEffect.NavigateHome, awaitItem())
                assertEquals(listOf(session.id), sessions.discarded)
            }
        }

    @Test
    fun outOfMemoryPagesAreSkippedAndReviewNavigates() =
        runTest {
            val vm = viewModel()
            // The engine retries an out-of-memory analysis once at a lower resolution: two scripts per page.
            repeat(4) { bridge.analyzeScript += FakeNativeBridge.Script.Fail(NativeStatus.OutOfMemory) }
            vm.onAction(ImportAction.Picked(photos(2)))
            advanceUntilIdle()
            assertEquals(ImportPhase.Finished, vm.uiState.value.phase)
            assertEquals(2, vm.uiState.value.skippedCount)
            vm.effects.test {
                assertEquals(ImportEffect.OpenPicker(PICKER_MAX_ITEMS), awaitItem())
                vm.onAction(ImportAction.Review)
                assertEquals(ImportEffect.NavigateToHandoff(sessions.state.value!!.id), awaitItem())
                vm.onAction(ImportAction.SeePro)
                assertEquals(ImportEffect.NavigateToPro, awaitItem())
            }
        }
}
