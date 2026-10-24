package app.plainpaper.feature.capture.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.plainpaper.core.capture.files.CopyResult
import app.plainpaper.core.capture.files.ImageFiles
import app.plainpaper.core.capture.files.ThumbnailLoader
import app.plainpaper.core.capture.picker.PickedPhoto
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.core.domain.model.capture.CaptureSession
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.usecase.capture.AddCapturedPageUseCase
import app.plainpaper.core.domain.usecase.capture.DiscardSessionUseCase
import app.plainpaper.core.domain.usecase.capture.EvaluateImportLimitUseCase
import app.plainpaper.core.domain.usecase.capture.ImportLimit
import app.plainpaper.core.domain.usecase.capture.ObserveActiveSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ResolveDefaultPageSizeUseCase
import app.plainpaper.core.domain.usecase.capture.SessionDirectoryUseCase
import app.plainpaper.core.domain.usecase.capture.StartSessionUseCase
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

/** Pickers that ignore the limit still return at most this many; the free rule is applied in Kotlin. */
const val PICKER_MAX_ITEMS = 100

/**
 * Import flow (spec US2): picker → free-limit sheet → copy every original into a session →
 * background conversion with per-photo tiles and a time estimate → cancel with keep/discard.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class ImportViewModel(
    private val evaluateLimit: EvaluateImportLimitUseCase,
    private val startSession: StartSessionUseCase,
    private val addPage: AddCapturedPageUseCase,
    private val discardSession: DiscardSessionUseCase,
    private val observeSession: ObserveActiveSessionUseCase,
    private val sessionDirectory: SessionDirectoryUseCase,
    private val resolvePageSize: ResolveDefaultPageSizeUseCase,
    private val files: ImageFiles,
    private val thumbnails: ThumbnailLoader,
    private val processor: PageProcessor,
    private val dispatchers: DispatcherProvider,
    private val now: () -> Long,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<ImportEffect>(Channel.BUFFERED)
    val effects: Flow<ImportEffect> = effectChannel.receiveAsFlow()

    private val estimate = ImportEstimate()
    private var picked: List<PickedPhoto> = emptyList()
    private var pageToItem = mutableMapOf<String, Int>()
    private var completedPages = mutableSetOf<String>()
    private var sessionJob: Job? = null
    private val loadedThumbnails = mutableSetOf<String>()

    init {
        effectChannel.trySend(ImportEffect.OpenPicker(PICKER_MAX_ITEMS))
    }

    fun onAction(action: ImportAction) {
        when (action) {
            is ImportAction.Picked -> {
                onPicked(action.photos)
            }

            ImportAction.ImportFirst -> {
                convert(picked.take(uiState.value.limit))
            }

            ImportAction.SeePro -> {
                effectChannel.trySend(ImportEffect.NavigateToPro)
            }

            ImportAction.Cancel -> {
                onCancel()
            }

            ImportAction.KeepConverted -> {
                onKeep()
            }

            ImportAction.DiscardConverted -> {
                onDiscard()
            }

            ImportAction.Review -> {
                uiState.value.sessionId?.let {
                    effectChannel.trySend(
                        ImportEffect.NavigateToHandoff(it),
                    )
                }
            }
        }
    }

    private fun onPicked(photos: List<PickedPhoto>) {
        if (photos.isEmpty()) {
            effectChannel.trySend(ImportEffect.NavigateBack)
            return
        }
        picked = photos
        viewModelScope.launch {
            when (val decision = evaluateLimit(photos.size)) {
                is ImportLimit.Decision.Proceed -> {
                    convert(photos)
                }

                is ImportLimit.Decision.ExceedsFreeLimit -> {
                    mutableState.update {
                        it.copy(
                            phase = ImportPhase.LimitSheet,
                            pickedCount = decision.picked,
                            limit = decision.limit,
                        )
                    }
                }
            }
        }
    }

    private fun convert(photos: List<PickedPhoto>) {
        mutableState.update {
            it.copy(
                phase = ImportPhase.Converting,
                items =
                    photos.mapIndexed {
                        index,
                        photo,
                        ->
                        ImportItem(index, photo.uri)
                    },
            )
        }
        viewModelScope.launch(dispatchers.io) {
            val session = startSession(SessionSource.Import)
            mutableState.update { it.copy(sessionId = session.id) }
            val directory = sessionDirectory(session.id)
            val pageSize = resolvePageSize(CaptureMode.Document)
            // Every original is copied before conversion starts (data-model.md ImportBatch).
            val pageIds = mutableListOf<String>()
            photos.forEachIndexed { index, photo ->
                val destination = "$directory/original-${index.toString().padStart(INDEX_DIGITS, '0')}.jpg"
                when (files.copyOriginal(photo.uri, destination)) {
                    is CopyResult.Copied -> {
                        val page = addPage(session.id, destination, PageTag.Document, pageSize)
                        pageToItem[page.id] = index
                        pageIds += page.id
                    }

                    is CopyResult.Failed -> {
                        markItem(index) { it.copy(state = ImportItemState.Skipped) }
                    }
                }
            }
            estimate.start(now())
            updateEstimate()
            sessionJob =
                viewModelScope.launch { observeSession().collect { s -> if (s?.id == session.id) onSession(s) } }
            pageIds.forEach { processor.enqueue(session.id, it) }
            if (pageIds.isEmpty()) finishIfDone()
        }
    }

    private fun onSession(session: CaptureSession) {
        session.pages.forEach { page ->
            val index = pageToItem[page.id] ?: return@forEach
            when (val state = page.state) {
                PageState.Pending -> {}

                PageState.Processing -> {
                    markItem(index) { it.copy(state = ImportItemState.Converting) }
                }

                PageState.Ready, is PageState.NeedsAttention -> {
                    complete(page.id)
                    markItem(index) { it.copy(state = ImportItemState.Done(page.id)) }
                    page.thumbnailPath?.let { path -> loadThumbnail(index, path) }
                }

                is PageState.Failed -> {
                    complete(page.id)
                    markItem(index) { it.copy(state = ImportItemState.Skipped) }
                }
            }
        }
        updateEstimate()
        finishIfDone()
    }

    private fun complete(pageId: String) {
        if (completedPages.add(pageId)) estimate.onCompleted(now())
    }

    private fun loadThumbnail(
        index: Int,
        path: String,
    ) {
        if (!loadedThumbnails.add(path)) return
        viewModelScope.launch(dispatchers.io) {
            val bitmap = thumbnails.load(path)
            markItem(index) { it.copy(thumbnail = bitmap) }
        }
    }

    private fun updateEstimate() {
        mutableState.update { state ->
            val remaining =
                state.items.count {
                    it.state is ImportItemState.Queued ||
                        it.state is ImportItemState.Converting
                }
            state.copy(estimatedSecondsLeft = estimate.secondsLeft(remaining))
        }
    }

    private fun finishIfDone() {
        mutableState.update { state ->
            val allDone =
                state.items.isNotEmpty() &&
                    state.items.all { it.state is ImportItemState.Done || it.state is ImportItemState.Skipped }
            if (allDone && state.phase == ImportPhase.Converting) state.copy(phase = ImportPhase.Finished) else state
        }
    }

    private fun markItem(
        index: Int,
        transform: (ImportItem) -> ImportItem,
    ) {
        mutableState.update { state ->
            state.copy(
                items =
                    state.items.map {
                        if (it.index ==
                            index
                        ) {
                            transform(it)
                        } else {
                            it
                        }
                    },
            )
        }
    }

    private fun onCancel() {
        val state = uiState.value
        when (state.phase) {
            ImportPhase.Picking, ImportPhase.LimitSheet -> {
                effectChannel.trySend(ImportEffect.NavigateBack)
            }

            ImportPhase.Converting -> {
                state.sessionId?.let { processor.cancel(it) }
                mutableState.update { it.copy(phase = ImportPhase.CancelChoice) }
            }

            ImportPhase.Finished -> {
                state.sessionId?.let { effectChannel.trySend(ImportEffect.NavigateToHandoff(it)) }
            }

            ImportPhase.CancelChoice -> {}
        }
    }

    private fun onKeep() {
        val sessionId = uiState.value.sessionId ?: return
        if (uiState.value.doneCount == 0) {
            onDiscard()
        } else {
            effectChannel.trySend(ImportEffect.NavigateToHandoff(sessionId))
        }
    }

    private fun onDiscard() {
        val sessionId = uiState.value.sessionId
        viewModelScope.launch {
            sessionJob?.cancel()
            if (sessionId != null) {
                processor.cancel(sessionId)
                discardSession(sessionId)
            }
            effectChannel.send(ImportEffect.NavigateHome)
        }
    }

    private companion object {
        const val INDEX_DIGITS = 3
    }
}
