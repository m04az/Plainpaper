package app.plainpaper.feature.capture.handoff

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.plainpaper.core.capture.files.OriginalPhotoLoader
import app.plainpaper.core.capture.files.ThumbnailLoader
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.Quad
import app.plainpaper.core.domain.model.capture.CaptureSession
import app.plainpaper.core.domain.model.capture.CapturedPage
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.usecase.capture.FinishSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ObserveActiveSessionUseCase
import app.plainpaper.feature.capture.crop.CropState
import app.plainpaper.feature.capture.crop.cropStateFor
import app.plainpaper.feature.capture.processing.PageProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Long edge of the large page preview (enough for a phone screen, cheap to decode). */
const val PREVIEW_EDGE = 1600

data class HandoffPage(
    val id: String,
    val position: Int,
    val state: PageState,
    val style: OutputStyle,
    val pagePath: String? = null,
    val thumbnail: ImageBitmap? = null,
) {
    /** A new look is being rendered. */
    val busy: Boolean get() = state is PageState.Pending || state is PageState.Processing
}

data class HandoffUiState(
    val pages: List<HandoffPage> = emptyList(),
    val processingRemaining: Int = 0,
    val current: Int = 0,
    /** Large image of the current page, null while it loads (the thumbnail stands in). */
    val preview: ImageBitmap? = null,
    /** The crop editor of the current page, open while non-null (spec 004 FR-014). */
    val crop: CropState? = null,
) {
    val currentPage: HandoffPage? get() = pages.getOrNull(current)
}

sealed interface HandoffAction {
    data object BackToHome : HandoffAction

    data class SelectPage(
        val index: Int,
    ) : HandoffAction

    data class ChooseLook(
        val style: OutputStyle,
    ) : HandoffAction

    data object ApplyLookToAll : HandoffAction

    data object RotatePage : HandoffAction

    data object FlipPage : HandoffAction

    data object OpenCrop : HandoffAction

    data object CloseCrop : HandoffAction

    data class ApplyCrop(
        val corners: Quad,
    ) : HandoffAction
}

sealed interface HandoffEffect {
    data object NavigateHome : HandoffEffect
}

/**
 * The page view after capture or import (spec 003 US6) until review arrives in Feature 4:
 * pages one at a time with the four looks and "apply to all".
 */
class HandoffViewModel(
    private val sessionId: String,
    observeSession: ObserveActiveSessionUseCase,
    private val finishSession: FinishSessionUseCase,
    private val processor: PageProcessor,
    private val thumbnails: ThumbnailLoader,
    private val originals: OriginalPhotoLoader,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HandoffUiState())
    val uiState: StateFlow<HandoffUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<HandoffEffect>(Channel.BUFFERED)
    val effects: Flow<HandoffEffect> = effectChannel.receiveAsFlow()

    private val loaded = mutableMapOf<String, ImageBitmap?>()
    private val loading = mutableSetOf<String>()
    private var handedOff = false
    private var previewPath: String? = null
    private var previewJob: Job? = null
    private var session: CaptureSession? = null

    init {
        viewModelScope.launch {
            observeSession().collect { session -> if (session?.id == sessionId) onSession(session) }
        }
    }

    fun onAction(action: HandoffAction) {
        when (action) {
            HandoffAction.BackToHome -> {
                effectChannel.trySend(HandoffEffect.NavigateHome)
            }

            is HandoffAction.SelectPage -> {
                select(action.index)
            }

            is HandoffAction.ChooseLook -> {
                uiState.value.currentPage?.let { processor.restyle(sessionId, listOf(it.id), action.style) }
            }

            HandoffAction.RotatePage -> {
                uiState.value.currentPage?.let { processor.transform(sessionId, it.id, rotateClockwise = true) }
            }

            HandoffAction.FlipPage -> {
                uiState.value.currentPage?.let { processor.transform(sessionId, it.id, flip = true) }
            }

            HandoffAction.OpenCrop -> {
                openCrop()
            }

            HandoffAction.CloseCrop -> {
                mutableState.update { it.copy(crop = null) }
            }

            is HandoffAction.ApplyCrop -> {
                applyCrop(action.corners)
            }

            HandoffAction.ApplyLookToAll -> {
                val style = uiState.value.currentPage?.style ?: return
                processor.restyle(sessionId, uiState.value.pages.map { it.id }, style)
            }
        }
    }

    private fun openCrop() {
        val page = currentSessionPage() ?: return
        if (!page.state.hasPage || uiState.value.crop != null) return
        mutableState.update { it.copy(crop = CropState(page.id)) }
        viewModelScope.launch(dispatchers.io) {
            val loaded = originals.cropStateFor(page)
            // Ignore a result that arrives after the editor was closed.
            mutableState.update { if (it.crop?.pageId == page.id) it.copy(crop = loaded) else it }
        }
    }

    private fun applyCrop(corners: Quad) {
        val crop = uiState.value.crop ?: return
        if (crop.photo == null) return
        mutableState.update { it.copy(crop = null) }
        processor.setCorners(sessionId, crop.pageId, corners)
    }

    private fun currentSessionPage(): CapturedPage? {
        val id = uiState.value.currentPage?.id ?: return null
        return session?.page(id)
    }

    private fun select(index: Int) {
        val clamped = index.coerceIn(0, maxOf(0, uiState.value.pages.size - 1))
        mutableState.update { it.copy(current = clamped) }
        refreshPreview()
    }

    private fun onSession(session: CaptureSession) {
        this.session = session
        if (!handedOff) {
            handedOff = true
            viewModelScope.launch { finishSession(sessionId) }
        }
        mutableState.update { state ->
            state.copy(
                pages =
                    session.pages.map { page ->
                        HandoffPage(
                            id = page.id,
                            position = page.position,
                            state = page.state,
                            style = page.style,
                            pagePath = page.pagePath,
                            thumbnail = page.thumbnailPath?.let(loaded::get),
                        )
                    },
                processingRemaining = session.unfinishedPages.size,
                current = state.current.coerceIn(0, maxOf(0, session.pageCount - 1)),
            )
        }
        session.pages.forEach { page -> page.thumbnailPath?.let { loadThumbnail(page.id, it) } }
        refreshPreview()
    }

    private fun loadThumbnail(
        pageId: String,
        path: String,
    ) {
        if (path in loaded || path in loading) return
        loading += path
        viewModelScope.launch(dispatchers.io) {
            val bitmap = thumbnails.load(path)
            loaded[path] = bitmap
            loading -= path
            mutableState.update { state ->
                state.copy(
                    pages =
                        state.pages.map {
                            if (it.id == pageId &&
                                it.thumbnail == null
                            ) {
                                it.copy(thumbnail = bitmap)
                            } else {
                                it
                            }
                        },
                )
            }
        }
    }

    /** Loads the large image of the current page whenever its rendered file changes. */
    private fun refreshPreview() {
        val path =
            uiState.value.currentPage
                ?.takeIf { !it.busy }
                ?.pagePath
        if (path == previewPath) return
        previewPath = path
        previewJob?.cancel()
        mutableState.update { it.copy(preview = null) }
        if (path == null) return
        previewJob =
            viewModelScope.launch(dispatchers.io) {
                val bitmap = thumbnails.load(path, PREVIEW_EDGE)
                mutableState.update { if (previewPath == path) it.copy(preview = bitmap) else it }
            }
    }
}
