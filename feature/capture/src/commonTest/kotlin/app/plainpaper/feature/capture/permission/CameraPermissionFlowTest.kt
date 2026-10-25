package app.plainpaper.feature.capture.permission

import app.cash.turbine.test
import app.plainpaper.core.capture.camera.CameraError
import app.plainpaper.core.capture.camera.CameraState
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.capture.CameraPermissionState
import app.plainpaper.core.domain.usecase.capture.AddCapturedPageUseCase
import app.plainpaper.core.domain.usecase.capture.DiscardSessionUseCase
import app.plainpaper.core.domain.usecase.capture.FinishSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ObserveActiveSessionUseCase
import app.plainpaper.core.domain.usecase.capture.RemovePageUseCase
import app.plainpaper.core.domain.usecase.capture.ResolveDefaultPageSizeUseCase
import app.plainpaper.core.domain.usecase.capture.SessionDirectoryUseCase
import app.plainpaper.core.domain.usecase.capture.StartSessionUseCase
import app.plainpaper.core.scanengine.DefaultScanEngine
import app.plainpaper.core.testing.FakeCapabilityProvider
import app.plainpaper.core.testing.FakeCaptureSessionRepository
import app.plainpaper.core.testing.FakeCaptureSettingsRepository
import app.plainpaper.core.testing.FakeNativeBridge
import app.plainpaper.core.testing.FakePhotoDecoder
import app.plainpaper.core.testing.FakeRegionProvider
import app.plainpaper.core.testing.FakeScanEngine
import app.plainpaper.core.testing.TestDispatcherProvider
import app.plainpaper.feature.capture.camera.CameraAction
import app.plainpaper.feature.capture.camera.CameraEffect
import app.plainpaper.feature.capture.camera.CameraViewModel
import app.plainpaper.feature.capture.fakes.FakeBackgroundTask
import app.plainpaper.feature.capture.fakes.FakeCameraController
import app.plainpaper.feature.capture.fakes.FakeCameraPermission
import app.plainpaper.feature.capture.fakes.FakeImageFiles
import app.plainpaper.feature.capture.fakes.FakeOriginalPhotoLoader
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec US5 (FR-017/FR-018): the screen owns a [FakeCameraPermission] the way the real route
 * does, and feeds the ViewModel through actions; this test drives both together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraPermissionFlowTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = TestDispatcherProvider(dispatcher)
    private val camera = FakeCameraController()
    private val permission = FakeCameraPermission()
    private val sessions = FakeCaptureSessionRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): CameraViewModel =
        CameraViewModel(
            cameraFactory = { camera },
            engine = FakeScanEngine(),
            processor =
                PageProcessor(
                    DefaultScanEngine(FakeNativeBridge(), FakeCapabilityProvider(DeviceCapability.Full), dispatchers),
                    FakePhotoDecoder(),
                    FakeImageFiles(),
                    sessions,
                    FakeBackgroundTask(),
                    dispatchers,
                    CoroutineScope(SupervisorJob() + dispatcher),
                ),
            startSession = StartSessionUseCase(sessions),
            addPage = AddCapturedPageUseCase(sessions),
            removePage = RemovePageUseCase(sessions),
            finishSession = FinishSessionUseCase(sessions),
            discardSession = DiscardSessionUseCase(sessions),
            observeSession = ObserveActiveSessionUseCase(sessions),
            sessionDirectory = SessionDirectoryUseCase(sessions),
            resolvePageSize = ResolveDefaultPageSizeUseCase(FakeRegionProvider()),
            settings = FakeCaptureSettingsRepository(),
            files = FakeImageFiles(),
            thumbnails = FakeThumbnailLoader(),
            originals = FakeOriginalPhotoLoader(),
            dispatchers = dispatchers,
        )

    /** What the route does on ON_START. */
    private fun CameraViewModel.screenStart() {
        onAction(CameraAction.PermissionStatus(permission.status()))
        onAction(CameraAction.ScreenStarted)
    }

    @Test
    fun firstVisitExplainsThenPromptsThenStarts() =
        runTest {
            val vm = viewModel()
            vm.screenStart()
            assertEquals(CameraPermissionState.NotAsked, vm.uiState.value.permission)
            assertEquals(0, camera.startCount)
            assertEquals(0, permission.requestCount)
            vm.effects.test {
                vm.onAction(CameraAction.AllowCamera)
                assertEquals(CameraEffect.RequestPermission, awaitItem())
                // The route performs the request and reports the result.
                vm.onAction(CameraAction.PermissionStatus(permission.request()))
            }
            assertEquals(1, permission.requestCount)
            assertEquals(CameraPermissionState.Granted, vm.uiState.value.permission)
            assertEquals(1, camera.startCount)
        }

    @Test
    fun deniedCanRetryUntilPermanentlyDenied() =
        runTest {
            permission.nextRequestResult = CameraPermissionState.Denied
            val vm = viewModel()
            vm.screenStart()
            vm.onAction(CameraAction.PermissionStatus(permission.request()))
            assertEquals(CameraPermissionState.Denied, vm.uiState.value.permission)
            assertEquals(0, camera.startCount)
            permission.nextRequestResult = CameraPermissionState.DeniedPermanently
            vm.onAction(CameraAction.PermissionStatus(permission.request()))
            assertEquals(CameraPermissionState.DeniedPermanently, vm.uiState.value.permission)
            vm.effects.test {
                vm.onAction(CameraAction.OpenSettings)
                assertEquals(CameraEffect.OpenSettings, awaitItem())
                permission.openSettings()
                assertEquals(1, permission.settingsOpened)
                vm.onAction(CameraAction.ImportInstead)
                assertEquals(CameraEffect.NavigateToImport(noCamera = false), awaitItem())
            }
            assertFalse(vm.uiState.value.showPreview)
        }

    @Test
    fun grantingInSettingsStartsTheCameraOnReturn() =
        runTest {
            permission.current = CameraPermissionState.DeniedPermanently
            val vm = viewModel()
            vm.screenStart()
            assertEquals(0, camera.startCount)
            vm.onAction(CameraAction.ScreenStopped)
            permission.current = CameraPermissionState.Granted // changed in Settings
            vm.screenStart()
            assertEquals(1, camera.startCount)
            assertTrue(vm.uiState.value.showPreview)
        }

    @Test
    fun noCameraGoesToImport() =
        runTest {
            permission.current = CameraPermissionState.Unavailable
            val vm = viewModel()
            vm.effects.test {
                vm.screenStart()
                assertEquals(CameraEffect.NavigateToImport(noCamera = true), awaitItem())
            }
            assertEquals(0, camera.startCount)
        }

    @Test
    fun cameraStartErrorWithoutACameraAlsoGoesToImport() =
        runTest {
            camera.startError = CameraState.Error(CameraError.Unavailable)
            permission.current = CameraPermissionState.Granted
            val vm = viewModel()
            vm.effects.test {
                vm.screenStart()
                assertEquals(CameraEffect.NavigateToImport(noCamera = true), awaitItem())
            }
        }
}
