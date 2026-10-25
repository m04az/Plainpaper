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
import app.plainpaper.core.capture.files.OriginalPhotoLoader
import app.plainpaper.core.capture.files.ThumbnailLoader
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.Quad
import app.plainpaper.core.domain.model.capture.CameraPermissionState
import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.core.domain.model.capture.CaptureSession
import app.plainpaper.core.domain.model.capture.CaptureSettings
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.model.capture.SessionStatus
import app.plainpaper.core.domain.repository.CaptureSettingsRepository
import app.plainpaper.core.domain.usecase.capture.AddCapturedPageUseCase
import app.plainpaper.core.domain.usecase.capture.DiscardSessionUseCase
import app.plainpaper.core.domain.usecase.capture.FinishSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ObserveActiveSessionUseCase
import app.plainpaper.core.domain.usecase.capture.RemovePageUseCase
import app.plainpaper.core.domain.usecase.capture.ResolveDefaultPageSizeUseCase
import app.plainpaper.core.domain.usecase.capture.SessionDirectoryUseCase
import app.plainpaper.core.domain.usecase.capture.StartSessionUseCase
import app.plainpaper.core.scanengine.ScanEngine
import app.plainpaper.feature.capture.crop.CropState
import app.plainpaper.feature.capture.crop.cropStateFor
import app.plainpaper.feature.capture.handoff.PREVIEW_EDGE
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
    private val removePage: RemovePageUseCase,
    private val finishSession: FinishSessionUseCase,
    private val discardSession: DiscardSessionUseCase,
    private val observeSession: ObserveActiveSessionUseCase,
    private val sessionDirectory: SessionDirectoryUseCase,
    private val resolvePageSize: ResolveDefaultPageSizeUseCase,
    private val settings: CaptureSettingsRepository,
    private val files: ImageFiles,
    private val thumbnails: ThumbnailLoader,
    private val originals: OriginalPhotoLoader,
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
    private var stabilizer: OutlineStabilizer? = null
    private var viewWidth = 0
    private var viewHeight = 0
    private var lastCorners: Quad? = null
    private var screenStarted = false
    private var frameJob: Job? = null
    private var startJob: Job? = null
    private var capturing = false
    private var loadedThumbnailPath: String? = null
    private var loadedPreviewPath: String? = null
    private var lastSession: CaptureSession? = null
    private var reviewTag: PageTag? = null

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

            is CameraAction.ReviewLook -> {
                withReview { processor.restyle(it.sessionId, listOf(it.pageId), action.style) }
            }

            CameraAction.ReviewRotate -> {
                withReview {
                    if (!it.review.processing) {
                        processor.transform(
                            it.sessionId,
                            it.pageId,
                            rotateClockwise = true,
                        )
                    }
                }
            }

            CameraAction.ReviewFlip -> {
                withReview { if (!it.review.processing) processor.transform(it.sessionId, it.pageId, flip = true) }
            }

            CameraAction.OpenCrop, CameraAction.CloseCrop, is CameraAction.ApplyCrop -> {
                onCropAction(action)
            }

            CameraAction.ReviewRetake -> {
                onRetake()
            }

            CameraAction.ReviewSave -> {
                onSave()
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
        if (uiState.value.review != null) {
            // Reviewing the last shot: no outline, no auto-capture.
            cameraFrame.release()
            return
        }
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
        // The gate decides on raw detections; the screen shows a stabilised outline (no flicker).
        val shown = stabilizerFor(geometry.frameDiagonal).onFrame(frame.timestampMillis, result.corners)
        val outline =
            shown?.takeIf { viewWidth > 0 && viewHeight > 0 }?.let { quad ->
                geometry.frameToView(quad).points.map { Offset(it.x, it.y) }
            }
        mutableState.update { it.copy(outline = outline, gate = gateState).withHint() }
        if (gateState == GateState.Capture) capture(manual = false)
    }

    private fun stabilizerFor(diagonal: Float): OutlineStabilizer =
        stabilizer?.takeIf { gateDiagonal == diagonal }
            ?: OutlineStabilizer(frameDiagonal = diagonal).also { stabilizer = it }

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
        if (capturing || !uiState.value.isRunning || uiState.value.review != null) return
        if (files.freeBytes(sessionDirectory(sessionId)) < ImageFiles.MIN_FREE_BYTES) {
            mutableState.update { it.copy(storageFull = true) }
            return
        }
        val startOutline = uiState.value.outline
        capturing = true
        mutableState.update { it.copy(capturing = true) }
        if (manual) gate?.enterCooldown(lastCorners)
        when (val result = camera.capture()) {
            is CaptureResult.Ready -> {
                val mode = uiState.value.mode
                val tag = sequencer.next(mode)
                val page = addPage(sessionId, result.originalPath, tag, resolvePageSize(mode))
                processor.enqueue(sessionId, page.id)
                reviewTag = tag
                loadedPreviewPath = null
                mutableState.update {
                    it
                        .copy(
                            awaitingCardBack = sequencer.awaitingBack,
                            outline = null,
                            review = ReviewState(page.id, startOutline),
                        ).withHint()
                }
                syncReview(lastSession)
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
        lastSession = session
        syncReview(session)
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

    // ---- review -------------------------------------------------------------------------------

    /** A snapshot of the page under review, handed to the action handlers. */
    private class ReviewContext(
        val sessionId: String,
        val review: ReviewState,
    ) {
        val pageId: String get() = review.pageId
    }

    private fun withReview(block: (ReviewContext) -> Unit) {
        val review = uiState.value.review ?: return
        val sessionId = uiState.value.sessionId ?: return
        block(ReviewContext(sessionId, review))
    }

    /** Mirrors the reviewed page's look, orientation and processing state; loads its large preview. */
    private fun syncReview(session: CaptureSession?) {
        val review = uiState.value.review ?: return
        val page = session?.page(review.pageId) ?: return
        val failed = page.state is PageState.Failed
        mutableState.update { state ->
            val current = state.review?.takeIf { it.pageId == page.id } ?: return@update state
            state.copy(
                review =
                    current.copy(
                        style = page.style,
                        rotation = page.rotation,
                        mirrored = page.mirrored,
                        processing = !page.state.hasPage && !failed,
                        failed = failed,
                    ),
            )
        }
        val path = page.pagePath?.takeIf { page.state.hasPage }
        if (path != null && path != loadedPreviewPath) {
            loadedPreviewPath = path
            viewModelScope.launch(dispatchers.io) {
                val bitmap = thumbnails.load(path, PREVIEW_EDGE)
                mutableState.update { state ->
                    val current = state.review
                    if (loadedPreviewPath == path && current?.pageId == page.id) {
                        state.copy(review = current.copy(preview = bitmap))
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun onCropAction(action: CameraAction) {
        when (action) {
            CameraAction.OpenCrop -> openCrop()
            CameraAction.CloseCrop -> setCrop(null)
            is CameraAction.ApplyCrop -> applyCrop(action.corners)
            else -> Unit
        }
    }

    private fun openCrop() {
        withReview { context ->
            val page = lastSession?.page(context.pageId) ?: return@withReview
            if (context.review.processing || context.review.failed || context.review.crop != null) return@withReview
            setCrop(CropState(page.id))
            viewModelScope.launch(dispatchers.io) {
                val loaded = originals.cropStateFor(page)
                mutableState.update { state ->
                    val review = state.review
                    // Ignore a result that arrives after the editor was closed or the page changed.
                    if (review?.pageId == page.id && review.crop != null) {
                        state.copy(review = review.copy(crop = loaded))
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun applyCrop(corners: Quad) {
        withReview { context ->
            if (context.review.crop?.photo == null) return@withReview
            setCrop(null)
            processor.setCorners(context.sessionId, context.pageId, corners)
        }
    }

    private fun setCrop(crop: CropState?) {
        mutableState.update { state -> state.review?.let { state.copy(review = it.copy(crop = crop)) } ?: state }
    }

    private fun onRetake() {
        withReview { context ->
            mutableState.update { it.copy(review = null) }
            reviewTag?.let(sequencer::rewind)
            mutableState.update { it.copy(awaitingCardBack = sequencer.awaitingBack).withHint() }
            viewModelScope.launch {
                processor.cancel(context.sessionId, context.pageId)
                removePage(context.sessionId, context.pageId)
            }
        }
    }

    private fun onSave() {
        if (uiState.value.review == null) return
        // The shot page is still in front of the camera: do not capture it again at once.
        gate?.enterCooldown(lastCorners)
        mutableState.update { it.copy(review = null) }
    }

    private fun onDone() {
        val sessionId = uiState.value.sessionId ?: return
        mutableState.update { it.copy(review = null) }
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
