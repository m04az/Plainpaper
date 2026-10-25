package app.plainpaper.feature.capture.camera

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import app.plainpaper.core.capture.camera.CameraState
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.capture.CameraPermissionState
import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.core.domain.model.capture.CapturedPage
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.feature.capture.crop.CropState

/** What the hint pill says (contracts/ui.md). */
enum class HintKind { HoldSteady, TapShutter, MoveCloser, TooDark, AdjustFraming, FrontOfCard, BackOfCard, FitReceipt }

/**
 * The page just shot, shown large over the camera until it is saved or retaken (spec 003 US7).
 * [startOutline] is the page outline at shutter time in view pixels, where the preview grows from.
 */
data class ReviewState(
    val pageId: String,
    val startOutline: List<Offset>? = null,
    val preview: ImageBitmap? = null,
    /** True while the page is (re-)rendered; the previous preview stays until the new one loads. */
    val processing: Boolean = true,
    val style: OutputStyle = CapturedPage.DEFAULT_STYLE,
    val rotation: Int = 0,
    val mirrored: Boolean = false,
    val failed: Boolean = false,
    /** The crop editor, open over the review while non-null (spec 004 FR-014). */
    val crop: CropState? = null,
)

data class CameraUiState(
    val permission: CameraPermissionState = CameraPermissionState.NotAsked,
    val camera: CameraState = CameraState.Idle,
    val mode: CaptureMode = CaptureMode.Document,
    val flash: Boolean = false,
    val autoCapture: Boolean = true,
    /** Outline corners (TL, TR, BR, BL) in preview-view pixels; null when no page is detected. */
    val outline: List<Offset>? = null,
    val gate: GateState = GateState.NoPage(),
    val hint: HintKind = HintKind.HoldSteady,
    val pageCount: Int = 0,
    val latestThumbnail: ImageBitmap? = null,
    val latestPageState: PageState? = null,
    val showDiscardConfirm: Boolean = false,
    val storageFull: Boolean = false,
    /** True for a moment after a capture (white flash, counter bump). */
    val capturing: Boolean = false,
    val captureFailed: Boolean = false,
    val sessionId: String? = null,
    /** Second side of an ID card is expected next (US4). */
    val awaitingCardBack: Boolean = false,
    /** Non-null while the last shot is being reviewed; frames are ignored meanwhile. */
    val review: ReviewState? = null,
) {
    val isRunning: Boolean get() = camera is CameraState.Running
    val showPreview: Boolean get() = permission == CameraPermissionState.Granted
}
