package app.plainpaper.feature.capture.quickscan

import app.cash.turbine.test
import app.plainpaper.core.capture.scanner.ScannerAvailability
import app.plainpaper.core.capture.scanner.ScannerResult
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.PageSource
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.model.capture.SessionStatus
import app.plainpaper.core.domain.usecase.capture.AddCapturedPageUseCase
import app.plainpaper.core.domain.usecase.capture.DiscardSessionUseCase
import app.plainpaper.core.domain.usecase.capture.FinishSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ObserveActiveSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ResolveDefaultPageSizeUseCase
import app.plainpaper.core.domain.usecase.capture.SessionDirectoryUseCase
import app.plainpaper.core.domain.usecase.capture.StartSessionUseCase
import app.plainpaper.core.scanengine.DefaultScanEngine
import app.plainpaper.core.testing.FakeCapabilityProvider
import app.plainpaper.core.testing.FakeCaptureSessionRepository
import app.plainpaper.core.testing.FakeNativeBridge
import app.plainpaper.core.testing.FakePhotoDecoder
import app.plainpaper.core.testing.FakeRegionProvider
import app.plainpaper.core.testing.TestDispatcherProvider
import app.plainpaper.feature.capture.fakes.FakeBackgroundTask
import app.plainpaper.feature.capture.fakes.FakeDocumentScanner
import app.plainpaper.feature.capture.fakes.FakeImageFiles
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QuickScanViewModelTest {
    private val sessions = FakeCaptureSessionRepository()
    private val scanner = FakeDocumentScanner()

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): QuickScanViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val dispatchers = TestDispatcherProvider(dispatcher)
        val processor =
            PageProcessor(
                engine =
                    DefaultScanEngine(
                        FakeNativeBridge(),
                        FakeCapabilityProvider(DeviceCapability.Full),
                        dispatchers,
                    ),
                decoder = FakePhotoDecoder(),
                files = FakeImageFiles(),
                sessions = sessions,
                background = FakeBackgroundTask(),
                dispatchers = dispatchers,
                scope = CoroutineScope(SupervisorJob() + dispatcher),
            )
        return QuickScanViewModel(
            startSession = StartSessionUseCase(sessions),
            addPage = AddCapturedPageUseCase(sessions),
            finishSession = FinishSessionUseCase(sessions),
            discardSession = DiscardSessionUseCase(sessions),
            observeSession = ObserveActiveSessionUseCase(sessions),
            sessionDirectory = SessionDirectoryUseCase(sessions),
            resolvePageSize = ResolveDefaultPageSizeUseCase(FakeRegionProvider("US")),
            processor = processor,
            dispatchers = dispatchers,
        )
    }

    /** The screen asks the scanner and reports its answer. */
    private suspend fun QuickScanViewModel.check(availability: ScannerAvailability) {
        scanner.availability = availability
        onAction(QuickScanAction.AvailabilityChecked(scanner.availability()))
    }

    /** The screen runs the scanner into the folder the ViewModel asked for and reports the result. */
    private suspend fun QuickScanViewModel.scan(
        open: QuickScanEffect.OpenScanner,
        result: ScannerResult,
    ) {
        scanner.result = result
        onAction(QuickScanAction.ScanFinished(scanner.scan(open.directory)))
    }

    @Test
    fun unavailableHidesTheActionAndIgnoresTaps() =
        runTest {
            val vm = viewModel()
            vm.check(ScannerAvailability.Unavailable)
            assertFalse(vm.uiState.value.visible)
            vm.onAction(QuickScanAction.Clicked(continueSession = false))
            advanceUntilIdle()
            assertNull(sessions.state.value)
            assertNull(vm.uiState.value.message)
        }

    @Test
    fun readyAndNeedsSetupShowTheAction() =
        runTest {
            val vm = viewModel()
            assertFalse(vm.uiState.value.visible, "hidden until the platform has answered")
            vm.check(ScannerAvailability.Ready)
            assertTrue(vm.uiState.value.visible)
            vm.check(ScannerAvailability.NeedsSetup)
            assertTrue(vm.uiState.value.visible)
        }

    @Test
    fun needsSetupShowsThePreparingMessageWithoutOpeningAnything() =
        runTest {
            val vm = viewModel()
            vm.check(ScannerAvailability.NeedsSetup)
            vm.effects.test {
                vm.onAction(QuickScanAction.Clicked(continueSession = false))
                advanceUntilIdle()
                assertEquals(QuickScanMessage.Preparing, vm.uiState.value.message)
                assertNull(sessions.state.value)
                expectNoEvents()
                vm.onAction(QuickScanAction.DismissMessage)
                assertNull(vm.uiState.value.message)
            }
        }

    @Test
    fun readyFromHomeStartsASessionAndAsksForTheScanner() =
        runTest {
            val vm = viewModel()
            vm.check(ScannerAvailability.Ready)
            vm.effects.test {
                vm.onAction(QuickScanAction.Clicked(continueSession = false))
                assertEquals(QuickScanEffect.OpenScanner("/fake/capture/s1"), awaitItem())
                assertTrue(vm.uiState.value.busy)
                assertEquals(SessionSource.Camera, sessions.state.value?.source)
            }
        }

    @Test
    fun pagesJoinTheSessionInOrderAsQuickScanAndTheViewerOpens() =
        runTest {
            val vm = viewModel()
            vm.check(ScannerAvailability.Ready)
            vm.effects.test {
                vm.onAction(QuickScanAction.Clicked(continueSession = false))
                val open = awaitItem() as QuickScanEffect.OpenScanner
                val paths = listOf("${open.directory}/original-000.jpg", "${open.directory}/original-001.jpg")
                vm.scan(open, ScannerResult.Pages(paths))
                assertEquals(listOf(open.directory), scanner.scannedInto)
                assertEquals(QuickScanEffect.NavigateToHandoff("s1"), awaitItem())
                val session = sessions.state.value!!
                assertEquals(paths, session.pages.map { it.originalPath })
                assertEquals(listOf(PageSource.QuickScan, PageSource.QuickScan), session.pages.map { it.source })
                assertEquals(listOf(PageTag.Document, PageTag.Document), session.pages.map { it.tag })
                assertEquals(PageSize.Letter, session.pages.first().pageSize)
                assertEquals(SessionStatus.HandedOff, session.status)
                assertFalse(vm.uiState.value.busy)
            }
        }

    @Test
    fun fromTheCameraTheActiveSessionIsContinued() =
        runTest {
            val vm = viewModel()
            val camera = sessions.start(SessionSource.Camera)
            sessions.addPage(camera.id, "/fake/capture/s1/original-000.jpg", PageTag.Document, PageSize.A4)
            vm.check(ScannerAvailability.Ready)
            vm.effects.test {
                vm.onAction(QuickScanAction.Clicked(continueSession = true))
                val open = awaitItem() as QuickScanEffect.OpenScanner
                assertEquals("/fake/capture/s1", open.directory)
                vm.scan(open, ScannerResult.Pages(listOf("/fake/capture/s1/original-001.jpg")))
                assertEquals(QuickScanEffect.NavigateToHandoff("s1"), awaitItem())
                assertEquals(
                    listOf(PageSource.Camera, PageSource.QuickScan),
                    sessions.state.value!!
                        .pages
                        .map { it.source },
                )
            }
        }

    @Test
    fun cancellingChangesNothingAndDropsTheEmptySession() =
        runTest {
            val vm = viewModel()
            vm.check(ScannerAvailability.Ready)
            vm.effects.test {
                vm.onAction(QuickScanAction.Clicked(continueSession = false))
                vm.scan(awaitItem() as QuickScanEffect.OpenScanner, ScannerResult.Cancelled)
                advanceUntilIdle()
                expectNoEvents()
                assertNull(sessions.state.value, "the empty session started for the scan is discarded")
                assertFalse(vm.uiState.value.busy)
                assertNull(vm.uiState.value.message)
            }
        }

    @Test
    fun cancellingKeepsTheCamerasSessionAndItsPages() =
        runTest {
            val vm = viewModel()
            val camera = sessions.start(SessionSource.Camera)
            sessions.addPage(camera.id, "/fake/capture/s1/original-000.jpg", PageTag.Document, PageSize.A4)
            vm.check(ScannerAvailability.Ready)
            vm.effects.test {
                vm.onAction(QuickScanAction.Clicked(continueSession = true))
                vm.scan(awaitItem() as QuickScanEffect.OpenScanner, ScannerResult.Cancelled)
                advanceUntilIdle()
                assertEquals(1, sessions.state.value?.pageCount)
                assertEquals(SessionStatus.Active, sessions.state.value?.status)
            }
        }

    @Test
    fun failureShowsTheErrorMessage() =
        runTest {
            val vm = viewModel()
            vm.check(ScannerAvailability.Ready)
            vm.effects.test {
                vm.onAction(QuickScanAction.Clicked(continueSession = false))
                vm.scan(awaitItem() as QuickScanEffect.OpenScanner, ScannerResult.Failed("boom"))
                advanceUntilIdle()
                expectNoEvents()
                assertEquals(QuickScanMessage.Failed, vm.uiState.value.message)
                assertFalse(vm.uiState.value.busy)
            }
        }

    @Test
    fun aSecondTapWhileTheScannerIsOpenIsIgnored() =
        runTest {
            val vm = viewModel()
            vm.check(ScannerAvailability.Ready)
            vm.effects.test {
                vm.onAction(QuickScanAction.Clicked(continueSession = false))
                awaitItem()
                vm.onAction(QuickScanAction.Clicked(continueSession = false))
                advanceUntilIdle()
                expectNoEvents()
            }
        }
}
