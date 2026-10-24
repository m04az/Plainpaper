package app.plainpaper.feature.capture.quickscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.plainpaper.core.capture.scanner.ScannerAvailability
import app.plainpaper.core.capture.scanner.ScannerResult
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.capture.CaptureMode
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
import app.plainpaper.feature.capture.processing.PageProcessor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the person is told after tapping Quick scan (contracts/ui.md). */
enum class QuickScanMessage { Preparing, Failed }

data class QuickScanUiState(
    val availability: ScannerAvailability = ScannerAvailability.Unavailable,
    val message: QuickScanMessage? = null,
    /** True from the tap until the scanner returns; further taps are ignored. */
    val busy: Boolean = false,
) {
    /** Quick scan is offered wherever a built-in scanner exists, even if it still has to be set up. */
    val visible: Boolean get() = availability != ScannerAvailability.Unavailable
}

sealed interface QuickScanAction {
    data class AvailabilityChecked(
        val availability: ScannerAvailability,
    ) : QuickScanAction

    /** [continueSession] adds to the camera's session; otherwise a new session is started (Home). */
    data class Clicked(
        val continueSession: Boolean,
    ) : QuickScanAction

    data class ScanFinished(
        val result: ScannerResult,
    ) : QuickScanAction

    data object DismissMessage : QuickScanAction
}

sealed interface QuickScanEffect {
    /** The screen runs `DocumentScanner.scan(directory)` and reports `ScanFinished`. */
    data class OpenScanner(
        val directory: String,
    ) : QuickScanEffect

    data class NavigateToHandoff(
        val sessionId: String,
    ) : QuickScanEffect
}

/**
 * Quick scan (spec 004 US2): the phone's own scanner writes cropped JPEGs into the capture
 * session folder; they join the session as [PageSource.QuickScan] pages and the page viewer opens.
 * The scanner itself is composable-scoped (it needs the Activity), so the screen runs it when this
 * ViewModel asks for it, as the Import screen does for the photo picker.
 */
@Suppress("LongParameterList") // use cases injected one by one, as in the other ViewModels
class QuickScanViewModel(
    private val startSession: StartSessionUseCase,
    private val addPage: AddCapturedPageUseCase,
    private val finishSession: FinishSessionUseCase,
    private val discardSession: DiscardSessionUseCase,
    private val observeSession: ObserveActiveSessionUseCase,
    private val sessionDirectory: SessionDirectoryUseCase,
    private val resolvePageSize: ResolveDefaultPageSizeUseCase,
    private val processor: PageProcessor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(QuickScanUiState())
    val uiState: StateFlow<QuickScanUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<QuickScanEffect>(Channel.BUFFERED)
    val effects: Flow<QuickScanEffect> = effectChannel.receiveAsFlow()

    private var sessionId: String? = null
    private var startedHere = false

    fun onAction(action: QuickScanAction) {
        when (action) {
            is QuickScanAction.AvailabilityChecked -> {
                mutableState.update { it.copy(availability = action.availability) }
            }

            is QuickScanAction.Clicked -> {
                onClicked(action.continueSession)
            }

            is QuickScanAction.ScanFinished -> {
                onFinished(action.result)
            }

            QuickScanAction.DismissMessage -> {
                mutableState.update { it.copy(message = null) }
            }
        }
    }

    private fun onClicked(continueSession: Boolean) {
        val state = uiState.value
        if (state.busy) return
        when (state.availability) {
            ScannerAvailability.Unavailable -> {
                mutableState.update { it.copy(message = null) } // hidden: nothing to do
            }

            ScannerAvailability.NeedsSetup -> {
                mutableState.update { it.copy(message = QuickScanMessage.Preparing) }
            }

            ScannerAvailability.Ready -> {
                mutableState.update { it.copy(busy = true, message = null) }
                viewModelScope.launch(dispatchers.io) {
                    val existing =
                        if (continueSession) {
                            observeSession().first()?.takeIf {
                                it.status == SessionStatus.Active && it.source == SessionSource.Camera
                            }
                        } else {
                            null
                        }
                    val session = existing ?: startSession(SessionSource.Camera)
                    sessionId = session.id
                    startedHere = existing == null
                    effectChannel.send(QuickScanEffect.OpenScanner(sessionDirectory(session.id)))
                }
            }
        }
    }

    private fun onFinished(result: ScannerResult) {
        val id = sessionId
        if (id == null) {
            mutableState.update { it.copy(busy = false) }
            return
        }
        viewModelScope.launch(dispatchers.io) {
            when (result) {
                is ScannerResult.Pages -> addAndOpen(id, result.paths)
                ScannerResult.Cancelled -> release(id, null)
                is ScannerResult.Failed -> release(id, QuickScanMessage.Failed)
            }
        }
    }

    private suspend fun addAndOpen(
        id: String,
        paths: List<String>,
    ) {
        if (paths.isEmpty()) {
            release(id, null)
            return
        }
        val size = resolvePageSize(CaptureMode.Document)
        paths.forEach { path ->
            val page = addPage(id, path, PageTag.Document, size, PageSource.QuickScan)
            processor.enqueue(id, page.id)
        }
        finishSession(id)
        sessionId = null
        mutableState.update { it.copy(busy = false) }
        effectChannel.send(QuickScanEffect.NavigateToHandoff(id))
    }

    /** Back to idle; a session this tap started and that stayed empty is not left behind. */
    private suspend fun release(
        id: String,
        message: QuickScanMessage?,
    ) {
        if (startedHere) {
            val empty = observeSession().first()?.takeIf { it.id == id }?.pageCount == 0
            if (empty) discardSession(id)
        }
        sessionId = null
        mutableState.update { it.copy(busy = false, message = message) }
    }
}
