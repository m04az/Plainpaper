package app.plainpaper.feature.capture.camera

import app.cash.turbine.test
import app.plainpaper.core.capture.camera.CameraState
import app.plainpaper.core.capture.camera.CaptureFailure
import app.plainpaper.core.capture.camera.CaptureResult
import app.plainpaper.core.domain.model.Confidence
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad
import app.plainpaper.core.domain.model.capture.CameraPermissionState
import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.core.domain.model.capture.CaptureSettings
import app.plainpaper.core.domain.model.capture.PageState
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
import app.plainpaper.core.scanengine.model.DetectResult
import app.plainpaper.core.testing.FakeCapabilityProvider
import app.plainpaper.core.testing.FakeCaptureSessionRepository
import app.plainpaper.core.testing.FakeCaptureSettingsRepository
import app.plainpaper.core.testing.FakeNativeBridge
import app.plainpaper.core.testing.FakePhotoDecoder
import app.plainpaper.core.testing.FakeRegionProvider
import app.plainpaper.core.testing.FakeScanEngine
import app.plainpaper.core.testing.TestDispatcherProvider
import app.plainpaper.feature.capture.fakes.FakeBackgroundTask
import app.plainpaper.feature.capture.fakes.FakeCameraController
import app.plainpaper.feature.capture.fakes.FakeImageFiles
import app.plainpaper.feature.capture.fakes.FakeThumbnailLoader
import app.plainpaper.feature.capture.processing.PageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = TestDispatcherProvider(dispatcher)
    private val camera = FakeCameraController()
    private val engine = FakeScanEngine()
    private val sessions = FakeCaptureSessionRepository()
    private val settings = FakeCaptureSettingsRepository()
    private val files = FakeImageFiles()
    private val thumbnails = FakeThumbnailLoader()
    private val region = FakeRegionProvider("EG")
    private val page = Quad(Point(100f, 60f), Point(540f, 60f), Point(540f, 420f), Point(100f, 420f))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun processor(): PageProcessor =
        PageProcessor(
            engine = DefaultScanEngine(FakeNativeBridge(), FakeCapabilityProvider(DeviceCapability.Full), dispatchers),
            decoder = FakePhotoDecoder(),
            files = files,
            sessions = sessions,
            background = FakeBackgroundTask(),
            dispatchers = dispatchers,
            scope = CoroutineScope(SupervisorJob() + dispatcher),
        )

    private fun viewModel(): CameraViewModel =
        CameraViewModel(
            cameraFactory = { camera },
            engine = engine,
            processor = processor(),
            startSession = StartSessionUseCase(sessions),
            addPage = AddCapturedPageUseCase(sessions),
            finishSession = FinishSessionUseCase(sessions),
            discardSession = DiscardSessionUseCase(sessions),
            observeSession = ObserveActiveSessionUseCase(sessions),
            sessionDirectory = SessionDirectoryUseCase(sessions),
            resolvePageSize = ResolveDefaultPageSizeUseCase(region),
            settings = settings,
            files = files,
            thumbnails = thumbnails,
            dispatchers = dispatchers,
        )

    /** Granted permission + started screen + a measured preview. */
    private fun runningViewModel(): CameraViewModel =
        viewModel().apply {
            onAction(CameraAction.PermissionStatus(CameraPermissionState.Granted))
            onAction(CameraAction.ScreenStarted)
            onAction(CameraAction.PreviewSizeChanged(390, 844))
        }

    private fun detected(corners: Quad? = page) {
        engine.nextDetectResult = {
            if (corners ==
                null
            ) {
                DetectResult.notFound(150f)
            } else {
                DetectResult(corners, Confidence.High, 0.6f, 150f, 1_000)
            }
        }
    }

    private suspend fun frames(
        from: Long,
        until: Long,
    ) {
        for (t in from..until step 100) camera.emitFrame(FakeCameraController.frame(timestampMillis = t))
    }

    @Test
    fun grantedPermissionStartsTheCameraInTheSessionFolder() =
        runTest {
            val vm = runningViewModel()
            assertEquals(1, camera.startCount)
            val sessionId = assertNotNull(vm.uiState.value.sessionId)
            assertEquals("/fake/capture/$sessionId", camera.outputDirectory)
            assertIs<CameraState.Running>(vm.uiState.value.camera)
            assertEquals(CameraPermissionState.Granted, vm.uiState.value.permission)
        }

    @Test
    fun cameraDoesNotStartBeforePermissionOrScreenStart() =
        runTest {
            val vm = viewModel()
            vm.onAction(CameraAction.ScreenStarted)
            assertEquals(0, camera.startCount)
            vm.onAction(CameraAction.PermissionStatus(CameraPermissionState.Granted))
            assertEquals(1, camera.startCount)
            vm.onAction(CameraAction.ScreenStopped)
            assertEquals(1, camera.stopCount)
            assertEquals(CameraState.Idle, vm.uiState.value.camera)
        }

    @Test
    fun framesProduceAnOutlineInViewCoordinatesAndAHint() =
        runTest {
            val vm = runningViewModel()
            detected()
            camera.emitFrame(FakeCameraController.frame(timestampMillis = 0))
            assertNull(vm.uiState.value.outline, "one frame is not enough to show an outline")
            camera.emitFrame(FakeCameraController.frame(timestampMillis = 33))
            val outline = assertNotNull(vm.uiState.value.outline)
            assertEquals(4, outline.size)
            assertTrue(outline.all { it.x in -200f..600f && it.y in 0f..844f })
            assertEquals(HintKind.HoldSteady, vm.uiState.value.hint)
            assertIs<GateState.Tracking>(vm.uiState.value.gate)
            detected(null)
            camera.emitFrame(FakeCameraController.frame(timestampMillis = 100))
            assertNotNull(vm.uiState.value.outline, "a short miss keeps the outline")
            camera.emitFrame(FakeCameraController.frame(timestampMillis = 600))
            assertNull(vm.uiState.value.outline)
        }

    @Test
    fun steadyPageIsCapturedAutomaticallyAndQueued() =
        runTest {
            val vm = runningViewModel()
            detected()
            frames(0, 700)
            assertEquals(1, camera.captures.size)
            val session = assertNotNull(sessions.state.value)
            assertEquals(1, session.pageCount)
            assertEquals(PageTag.Document, session.pages.single().tag)
            assertEquals(PageSize.A4, session.pages.single().pageSize)
            assertEquals(1, vm.uiState.value.pageCount)
            assertTrue(vm.uiState.value.capturing)
            advanceTimeBy(400)
            assertFalse(vm.uiState.value.capturing)
            // The page was processed by the (fake-backed) engine and its thumbnail requested.
            assertEquals(PageState.Ready, vm.uiState.value.latestPageState)
            assertEquals(listOf("/fake/capture/${session.id}/thumb-p1-r0-colordocument.jpg"), thumbnails.requested)
            // Still steady: cooldown, no second capture.
            frames(800, 2_000)
            assertEquals(1, camera.captures.size)
        }

    @Test
    fun shutterCapturesRegardlessOfDetection() =
        runTest {
            val vm = runningViewModel()
            detected(null)
            camera.emitFrame(FakeCameraController.frame())
            vm.onAction(CameraAction.Shutter)
            assertEquals(1, camera.captures.size)
            assertEquals(1, vm.uiState.value.pageCount)
        }

    @Test
    fun autoOffNeverCapturesAndChangesTheHint() =
        runTest {
            val vm = runningViewModel()
            vm.onAction(CameraAction.SetAutoCapture(false))
            assertFalse(settings.state.value.autoCapture)
            assertEquals(HintKind.TapShutter, vm.uiState.value.hint)
            detected()
            frames(0, 2_000)
            assertTrue(camera.captures.isEmpty())
        }

    @Test
    fun flashIsPersistedAndForwarded() =
        runTest {
            val vm = runningViewModel()
            vm.onAction(CameraAction.SetFlash(true))
            assertTrue(settings.state.value.flash)
            assertTrue(camera.flash)
            assertTrue(vm.uiState.value.flash)
        }

    @Test
    fun settingsAreRestoredOnStart() =
        runTest {
            settings.state.value = CaptureSettings(flash = true, autoCapture = false, mode = CaptureMode.Receipt)
            val vm = runningViewModel()
            assertTrue(vm.uiState.value.flash)
            assertFalse(vm.uiState.value.autoCapture)
            assertEquals(CaptureMode.Receipt, vm.uiState.value.mode)
            assertEquals(HintKind.FitReceipt, vm.uiState.value.hint)
            assertTrue(camera.flash)
        }

    @Test
    fun doneHandsTheSessionOver() =
        runTest {
            val vm = runningViewModel()
            vm.onAction(CameraAction.Shutter)
            vm.effects.test {
                vm.onAction(CameraAction.Done)
                val sessionId = vm.uiState.value.sessionId!!
                assertEquals(CameraEffect.NavigateToHandoff(sessionId), awaitItem())
                assertEquals(SessionStatus.HandedOff, sessions.state.value?.status)
            }
        }

    @Test
    fun captureFailureIsReportedAndRecoverable() =
        runTest {
            val vm = runningViewModel()
            camera.nextCapture = { CaptureResult.Failed(CaptureFailure.Internal) }
            vm.onAction(CameraAction.Shutter)
            assertTrue(vm.uiState.value.captureFailed)
            assertEquals(0, vm.uiState.value.pageCount)
            vm.onAction(CameraAction.DismissCaptureFailed)
            assertFalse(vm.uiState.value.captureFailed)
        }

    @Test
    fun storageFullBlocksCapture() =
        runTest {
            val vm = runningViewModel()
            files.freeBytes = 10L * 1024 * 1024
            vm.onAction(CameraAction.Shutter)
            assertTrue(vm.uiState.value.storageFull)
            assertTrue(camera.captures.isEmpty())
        }

    @Test
    fun closeAsksBeforeDiscardingPages() =
        runTest {
            val vm = runningViewModel()
            vm.effects.test {
                vm.onAction(CameraAction.Close)
                assertEquals(CameraEffect.NavigateBack, awaitItem())
                vm.onAction(CameraAction.Shutter)
                vm.onAction(CameraAction.Close)
                assertTrue(vm.uiState.value.showDiscardConfirm)
                vm.onAction(CameraAction.KeepScanning)
                assertFalse(vm.uiState.value.showDiscardConfirm)
                vm.onAction(CameraAction.Close)
                val sessionId = vm.uiState.value.sessionId!!
                vm.onAction(CameraAction.ConfirmDiscard)
                assertEquals(CameraEffect.NavigateBack, awaitItem())
                assertEquals(listOf(sessionId), sessions.discarded)
            }
        }

    @Test
    fun importInsteadAndSettingsAndAllowEmitEffects() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onAction(CameraAction.AllowCamera)
                assertEquals(CameraEffect.RequestPermission, awaitItem())
                vm.onAction(CameraAction.OpenSettings)
                assertEquals(CameraEffect.OpenSettings, awaitItem())
                vm.onAction(CameraAction.ImportInstead)
                assertEquals(CameraEffect.NavigateToImport(noCamera = false), awaitItem())
                vm.onAction(CameraAction.PermissionStatus(CameraPermissionState.Unavailable))
                assertEquals(CameraEffect.NavigateToImport(noCamera = true), awaitItem())
            }
            assertEquals(0, camera.startCount)
        }

    // ---- US3: many pages, lifecycle ------------------------------------------------------------

    @Test
    fun screenStopAndStartRestartTheCameraButKeepTheSession() =
        runTest {
            val vm = runningViewModel()
            vm.onAction(CameraAction.Shutter)
            val sessionId = vm.uiState.value.sessionId
            vm.onAction(CameraAction.ScreenStopped)
            assertEquals(1, camera.stopCount)
            vm.onAction(CameraAction.ScreenStarted)
            assertEquals(2, camera.startCount)
            assertEquals(sessionId, vm.uiState.value.sessionId)
            assertEquals(1, vm.uiState.value.pageCount)
        }

    @Test
    fun thirtyCapturesKeepOrderAndCount() =
        runTest {
            val vm = runningViewModel()
            repeat(30) {
                vm.onAction(CameraAction.Shutter)
                advanceTimeBy(400)
            }
            val session = assertNotNull(sessions.state.value)
            assertEquals(30, session.pageCount)
            assertEquals((0 until 30).toList(), session.pages.map { it.position })
            assertEquals(30, vm.uiState.value.pageCount)
        }

    @Test
    fun doneWhileProcessingStillHandsOver() =
        runTest {
            val vm = runningViewModel()
            vm.onAction(CameraAction.Shutter)
            vm.effects.test {
                vm.onAction(CameraAction.Done)
                assertIs<CameraEffect.NavigateToHandoff>(awaitItem())
            }
        }

    @Test
    fun anExistingActiveCameraSessionIsReused() =
        runTest {
            val existing = sessions.start(SessionSource.Camera)
            sessions.addPage(existing.id, "/o.jpg", PageTag.Document, PageSize.A4)
            val vm = runningViewModel()
            assertEquals(existing.id, vm.uiState.value.sessionId)
            assertEquals(1, vm.uiState.value.pageCount)
        }

    // ---- US4: modes ------------------------------------------------------------------------

    @Test
    fun selectingAModePersistsAndChangesTheHintAndTags() =
        runTest {
            val vm = runningViewModel()
            vm.onAction(CameraAction.SelectMode(CaptureMode.IdCard))
            assertEquals(CaptureMode.IdCard, settings.state.value.mode)
            assertEquals(HintKind.FrontOfCard, vm.uiState.value.hint)
            vm.onAction(CameraAction.Shutter)
            advanceTimeBy(400)
            assertEquals(HintKind.BackOfCard, vm.uiState.value.hint)
            vm.onAction(CameraAction.Shutter)
            advanceTimeBy(400)
            assertEquals(HintKind.FrontOfCard, vm.uiState.value.hint)
            vm.onAction(CameraAction.SelectMode(CaptureMode.Receipt))
            assertEquals(HintKind.FitReceipt, vm.uiState.value.hint)
            vm.onAction(CameraAction.Shutter)
            val pages = assertNotNull(sessions.state.value).pages
            assertIs<PageTag.IdFront>(pages[0].tag)
            assertIs<PageTag.IdBack>(pages[1].tag)
            assertEquals((pages[0].tag as PageTag.IdFront).cardId, (pages[1].tag as PageTag.IdBack).cardId)
            assertEquals(PageTag.Receipt, pages[2].tag)
            assertTrue(pages.all { it.pageSize == PageSize.Natural })
        }
}
