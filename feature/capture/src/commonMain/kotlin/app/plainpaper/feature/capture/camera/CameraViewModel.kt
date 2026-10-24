package app.plainpaper.feature.capture.camera

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.plainpaper.core.capture.camera.CameraController
import app.plainpaper.core.capture.camera.CameraControllerFactory
import app.plainpaper.core.capture.camera.CameraError
import app.plainpaper.core.capture.camera.CameraFrame
import app.plainpaper.core.capture.camera.CameraState
import app.plainpaper.core.capture.camera.CaptureResult
import app.plainpaper.core.capture.camera.PreviewGeometry
import app.plainpaper.core.capture.files.ImageFiles
import app.plainpaper.core.capture.files.ThumbnailLoader
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.Quad
import app.plainpaper.core.domain.model.capture.CameraPermissionState
import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.core.domain.model.capture.CaptureSession
import app.plainpaper.core.domain.model.capture.CaptureSettings
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.model.capture.SessionStatus
import app.plainpaper.core.domain.repository.CaptureSettingsRepository
import app.plainpaper.core.domain.usecase.capture.AddCapturedPageUseCase
import app.plainpaper.core.domain.usecase.capture.DiscardSessionUseCase
import app.plainpaper.core.domain.usecase.capture.FinishSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ObserveActiveSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ResolveDefaultPageSizeUseCase
import app.plainpaper.core.domain.usecase.capture.SessionDirectoryUseCase
import app.plainpaper.core.domain.usecase.capture.StartSessionUseCase
import app.plainpaper.core.scanengine.ScanEngine
import app.plainpaper.feature.capture.processing.PageProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CAPTURE_FLASH_MILLIS = 300L

/**
 * Camera screen state machine (spec US1, US3–US5): permission, camera lifecycle, live outline
 * and auto-capture gate, captures into the session, flash/Auto/mode settings, Done and discard.
 */
@Suppress("LongParameterList", "TooManyFunctions") // one collaborator per responsibility; one handler per action
class CameraViewModel(
    cameraFactory: CameraControllerFactory,
    private val engine: ScanEngine,
    private val processor: PageProcessor,
    private val startSession: StartSessionUseCase,
    private val addPage: AddCapturedPageUseCase,
    private val finishSession: FinishSessionUseCase,
    private val discardSession: DiscardSessionUseCase,
    private val observeSession: ObserveActiveSessionUseCase,
    private val sessionDirectory: SessionDirectoryUseCase,
    private val resolvePageSize: ResolveDefaultPageSizeUseCase,
    private val settings: CaptureSettingsRepository,
    private val files: ImageFiles,
    private val thumbnails: ThumbnailLoader,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    /** The platform camera; the screen hands it to the preview renderer. */
    val camera: CameraController = cameraFactory.create()
    private val mutableState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CameraEffect>(Channel.BUFFERED)
    val effects: Flow<CameraEffect> = effectChannel.receiveAsFlow()

    private val sequencer = ModeSequencer()
    private var gate: AutoCaptureGate? = null
    private var gateDiagonal = 0f
    private var viewWidth = 0
    private var viewHeight = 0
    private var lastCorners: Quad? = null
    private var screenStarted = false
    private var frameJob: Job? = null
    private var startJob: Job? = null
    private var capturing = false
    private var loadedThumbnailPath: String? = null

    init {
        viewModelScope.launch {
            settings.settings.collect { applySettings(it) }
        }
        viewModelScope.launch {
            camera.state.collect { state ->
                mutableState.update { it.copy(camera = state) }
                if (state == CameraState.Error(CameraError.Unavailable)) {
                    effectChannel.send(CameraEffect.NavigateToImport(noCamera = true))
                }
            }
        }
        viewModelScope.launch {
            observeSession().collect { session -> onSession(session) }
        }
    }

    @Suppress("CyclomaticComplexMethod") // one branch per user action; each delegates to a handler
    fun onAction(action: CameraAction) {
        when (action) {
            is CameraAction.PermissionStatus -> {
                onPermission(action.state)
            }

            CameraAction.AllowCamera -> {
                effectChannel.trySend(CameraEffect.RequestPermission)
            }

            CameraAction.OpenSettings -> {
                effectChannel.trySend(CameraEffect.OpenSettings)
            }

            CameraAction.ImportInstead -> {
                effectChannel.trySend(CameraEffect.NavigateToImport(noCamera = false))
            }

            CameraAction.ScreenStarted -> {
                onScreenStarted()
            }

            CameraAction.ScreenStopped -> {
                onScreenStopped()
            }

            is CameraAction.PreviewSizeChanged -> {
                viewWidth = action.width
                viewHeight = action.height
            }

            CameraAction.Shutter -> {
                viewModelScope.launch { capture(manual = true) }
            }

            is CameraAction.SetFlash -> {
                persist { it.copy(flash = action.enabled) }
            }

            is CameraAction.SetAutoCapture -> {
                persist { it.copy(autoCapture = action.enabled) }
            }

            is CameraAction.SelectMode -> {
                persist { it.copy(mode = action.mode) }
            }

            CameraAction.Done -> {
                onDone()
            }

            CameraAction.Close -> {
                onClose()
            }

            CameraAction.ConfirmDiscard -> {
                onConfirmDiscard()
            }

            CameraAction.KeepScanning -> {
                mutableState.update { it.copy(showDiscardConfirm = false) }
            }

            CameraAction.DismissCaptureFailed -> {
                mutableState.update { it.copy(captureFailed = false) }
            }
        }
    }

    // ---- permission and lifecycle -----------------------------------------------------------

    private fun onPermission(state: CameraPermissionState) {
        mutableState.update { it.copy(permission = state) }
        when (state) {
            CameraPermissionState.Granted -> {
                if (screenStarted) ensureRunning()
            }

            CameraPermissionState.Unavailable -> {
                effectChannel.trySend(CameraEffect.NavigateToImport(noCamera = true))
            }

            else -> {}
        }
    }

    private fun onScreenStarted() {
        screenStarted = true
        if (uiState.value.permission == CameraPermissionState.Granted) ensureRunning()
    }

    private fun onScreenStopped() {
        screenStarted = false
        startJob?.cancel()
        startJob = null
        frameJob?.cancel()
        frameJob = null
        camera.stop()
    }

    private fun ensureRunning() {
        if (startJob?.isActive == true || frameJob?.isActive == true) return
        startJob =
            viewModelScope.launch {
                val session = currentOrNewSession()
                camera.start(sessionDirectory(session.id))
                camera.setFlash(uiState.value.flash)
                frameJob = viewModelScope.launch(dispatchers.default) { camera.frames.collect { onFrame(it) } }
            }
    }

    private suspend fun currentOrNewSession(): CaptureSession {
        val existing =
            observeSession().first()?.takeIf {
                it.status == SessionStatus.Active &&
                    it.source == SessionSource.Camera
            }
        val session = existing ?: startSession(SessionSource.Camera)
        mutableState.update { it.copy(sessionId = session.id) }
        return session
    }

    // ---- frames -------------------------------------------------------------------------------

    private suspend fun onFrame(cameraFrame: CameraFrame) {
        val frame = cameraFrame.frame
        val geometry = PreviewGeometry(frame.width, frame.height, cameraFrame.rotationDegrees, viewWidth, viewHeight)
        val gate = gateFor(geometry.frameDiagonal)
        val result =
            try {
                engine.detect(frame)
            } finally {
                cameraFrame.release()
            }
        val sample =
            DetectionSample(
                frame.timestampMillis,
                result.corners,
                result.confidence,
                result.fillFraction,
                result.meanLuma,
            )
        val gateState = gate.onSample(sample)
        lastCorners = result.corners
        val outline =
            result.corners?.takeIf { viewWidth > 0 && viewHeight > 0 }?.let { quad ->
                geometry.frameToView(quad).points.map { Offset(it.x, it.y) }
            }
        mutableState.update { it.copy(outline = outline, gate = gateState).withHint() }
        if (gateState == GateState.Capture) capture(manual = false)
    }

    private fun gateFor(diagonal: Float): AutoCaptureGate {
        val current = gate
        if (current != null && gateDiagonal == diagonal) return current
        val window =
            if (engine.capability == DeviceCapability.Full) {
                AutoCaptureGate.DEFAULT_STEADY_WINDOW_MILLIS
            } else {
                AutoCaptureGate.SIMPLIFIED_STEADY_WINDOW_MILLIS
            }
        return AutoCaptureGate(frameDiagonal = diagonal, steadyWindowMillis = window)
            .also {
                it.enabled = uiState.value.autoCapture
                gate = it
                gateDiagonal = diagonal
            }
    }

    // ---- capture ----------------------------------------------------------------------------

    private suspend fun capture(manual: Boolean) {
        val sessionId = uiState.value.sessionId ?: return
        if (capturing || !uiState.value.isRunning) return
        if (files.freeBytes(sessionDirectory(sessionId)) < ImageFiles.MIN_FREE_BYTES) {
            mutableState.update { it.copy(storageFull = true) }
            return
        }
        capturing = true
        mutableState.update { it.copy(capturing = true) }
        if (manual) gate?.enterCooldown(lastCorners)
        when (val result = camera.capture()) {
            is CaptureResult.Ready -> {
                val mode = uiState.value.mode
                val tag = sequencer.next(mode)
                val page = addPage(sessionId, result.originalPath, tag, resolvePageSize(mode))
                processor.enqueue(sessionId, page.id)
                mutableState.update { it.copy(awaitingCardBack = sequencer.awaitingBack).withHint() }
            }

            is CaptureResult.Failed -> {
                mutableState.update { it.copy(captureFailed = true) }
            }
        }
        delay(CAPTURE_FLASH_MILLIS)
        capturing = false
        mutableState.update { it.copy(capturing = false) }
    }

    // ---- session ------------------------------------------------------------------------------

    private fun onSession(session: CaptureSession?) {
        val mine = session?.takeIf { it.id == uiState.value.sessionId || uiState.value.sessionId == null }
        val latest = mine?.pages?.lastOrNull()
        mutableState.update {
            it.copy(
                pageCount = mine?.pageCount ?: 0,
                latestPageState = latest?.state,
                latestThumbnail = if (latest?.thumbnailPath == null) null else it.latestThumbnail,
            )
        }
        val path = latest?.thumbnailPath
        if (path != null && path != loadedThumbnailPath) {
            loadedThumbnailPath = path
            viewModelScope.launch(dispatchers.io) {
                val bitmap = thumbnails.load(path)
                mutableState.update { if (loadedThumbnailPath == path) it.copy(latestThumbnail = bitmap) else it }
            }
        }
    }

    private fun onDone() {
        val sessionId = uiState.value.sessionId ?: return
        viewModelScope.launch {
            finishSession(sessionId)
            effectChannel.send(CameraEffect.NavigateToHandoff(sessionId))
        }
    }

    private fun onClose() {
        if (uiState.value.pageCount > 0) {
            mutableState.update { it.copy(showDiscardConfirm = true) }
        } else {
            effectChannel.trySend(CameraEffect.NavigateBack)
        }
    }

    private fun onConfirmDiscard() {
        val sessionId = uiState.value.sessionId
        mutableState.update { it.copy(showDiscardConfirm = false) }
        viewModelScope.launch {
            if (sessionId != null) {
                processor.cancel(sessionId)
                discardSession(sessionId)
            }
            effectChannel.send(CameraEffect.NavigateBack)
        }
    }

    // ---- settings -----------------------------------------------------------------------------

    private fun applySettings(value: CaptureSettings) {
        val previousMode = uiState.value.mode
        mutableState.update {
            it
                .copy(
                    flash = value.flash,
                    autoCapture = value.autoCapture,
                    mode = value.mode,
                ).withHint()
        }
        gate?.enabled = value.autoCapture
        camera.setFlash(value.flash)
        if (previousMode != value.mode) {
            sequencer.onModeChanged(value.mode)
            mutableState.update { it.copy(awaitingCardBack = sequencer.awaitingBack).withHint() }
        }
    }

    private fun persist(transform: (CaptureSettings) -> CaptureSettings) {
        viewModelScope.launch(dispatchers.io) {
            settings.set(transform(settings.settings.first()))
        }
    }

    private fun CameraUiState.withHint(): CameraUiState = copy(hint = hintFor(this))

    private fun hintFor(state: CameraUiState): HintKind =
        when (val gate = state.gate) {
            GateState.TooDark -> HintKind.TooDark
            GateState.LowConfidence -> HintKind.AdjustFraming
            is GateState.NoPage -> if (gate.prolonged) HintKind.MoveCloser else modeHint(state)
            else -> modeHint(state)
        }

    private fun modeHint(state: CameraUiState): HintKind =
        when (state.mode) {
            CaptureMode.IdCard -> if (state.awaitingCardBack) HintKind.BackOfCard else HintKind.FrontOfCard
            CaptureMode.Receipt -> HintKind.FitReceipt
            CaptureMode.Document -> if (state.autoCapture) HintKind.HoldSteady else HintKind.TapShutter
        }

    override fun onCleared() {
        camera.stop()
    }
}
