package app.plainpaper.feature.capture.import

import androidx.compose.ui.graphics.ImageBitmap
import app.plainpaper.core.capture.picker.PickedPhoto

enum class ImportPhase { Picking, LimitSheet, Converting, Finished, CancelChoice }

sealed interface ImportItemState {
    data object Queued : ImportItemState

    data object Converting : ImportItemState

    data class Done(
        val pageId: String,
    ) : ImportItemState

    data object Skipped : ImportItemState
}

data class ImportItem(
    val index: Int,
    val uri: String,
    val state: ImportItemState = ImportItemState.Queued,
    val thumbnail: ImageBitmap? = null,
)

data class ImportUiState(
    val phase: ImportPhase = ImportPhase.Picking,
    val items: List<ImportItem> = emptyList(),
    val pickedCount: Int = 0,
    val limit: Int = 0,
    val sessionId: String? = null,
    val estimatedSecondsLeft: Int = 0,
) {
    val total: Int get() = items.size
    val doneCount: Int get() = items.count { it.state is ImportItemState.Done }
    val skippedCount: Int get() = items.count { it.state is ImportItemState.Skipped }
    val convertedCount: Int get() = doneCount
    val finishedCount: Int get() = doneCount + skippedCount
}

sealed interface ImportAction {
    data class Picked(
        val photos: List<PickedPhoto>,
    ) : ImportAction

    data object ImportFirst : ImportAction

    data object SeePro : ImportAction

    data object Cancel : ImportAction

    data object KeepConverted : ImportAction

    data object DiscardConverted : ImportAction

    data object Review : ImportAction
}

sealed interface ImportEffect {
    data class OpenPicker(
        val maxItems: Int,
    ) : ImportEffect

    data object NavigateBack : ImportEffect

    data object NavigateHome : ImportEffect

    data class NavigateToHandoff(
        val sessionId: String,
    ) : ImportEffect

    data object NavigateToPro : ImportEffect
}
