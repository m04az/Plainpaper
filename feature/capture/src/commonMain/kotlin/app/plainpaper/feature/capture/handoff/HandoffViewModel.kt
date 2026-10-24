package app.plainpaper.feature.capture.handoff

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.plainpaper.core.capture.files.ThumbnailLoader
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.capture.CaptureSession
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.usecase.capture.FinishSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ObserveActiveSessionUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HandoffPage(
    val id: String,
    val position: Int,
    val state: PageState,
    val thumbnail: ImageBitmap? = null,
)

data class HandoffUiState(
    val pages: List<HandoffPage> = emptyList(),
    val processingRemaining: Int = 0,
)

sealed interface HandoffAction {
    data object BackToHome : HandoffAction
}

sealed interface HandoffEffect {
    data object NavigateHome : HandoffEffect
}

/** Placeholder review hand-off until Feature 4: lists the session's pages as they finish. */
class HandoffViewModel(
    private val sessionId: String,
    observeSession: ObserveActiveSessionUseCase,
    private val finishSession: FinishSessionUseCase,
    private val thumbnails: ThumbnailLoader,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HandoffUiState())
    val uiState: StateFlow<HandoffUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<HandoffEffect>(Channel.BUFFERED)
    val effects: Flow<HandoffEffect> = effectChannel.receiveAsFlow()

    private val loaded = mutableMapOf<String, ImageBitmap?>()
    private val loading = mutableSetOf<String>()

    private var handedOff = false

    init {
        viewModelScope.launch {
            observeSession().collect { session -> if (session?.id == sessionId) onSession(session) }
        }
    }

    fun onAction(action: HandoffAction) {
        when (action) {
            HandoffAction.BackToHome -> effectChannel.trySend(HandoffEffect.NavigateHome)
        }
    }

    private fun onSession(session: CaptureSession) {
        if (!handedOff) {
            handedOff = true
            viewModelScope.launch { finishSession(sessionId) }
        }
        mutableState.update {
            HandoffUiState(
                pages =
                    session.pages.map { page ->
                        HandoffPage(page.id, page.position, page.state, page.thumbnailPath?.let(loaded::get))
                    },
                processingRemaining = session.unfinishedPages.size,
            )
        }
        session.pages.forEach { page ->
            val path = page.thumbnailPath ?: return@forEach
            if (path in loaded || path in loading) return@forEach
            loading += path
            viewModelScope.launch(dispatchers.io) {
                val bitmap = thumbnails.load(path)
                loaded[path] = bitmap
                loading -= path
                mutableState.update { state ->
                    state.copy(pages = state.pages.map { if (it.id == page.id) it.copy(thumbnail = bitmap) else it })
                }
            }
        }
    }
}
