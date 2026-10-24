package app.plainpaper.core.domain.model.capture

import app.plainpaper.core.domain.model.Confidence
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.Quad

/** Where a page came from; Quick scan pages are already cropped by the phone's scanner (spec 004 FR-012). */
public enum class PageSource { Camera, Import, QuickScan }

/** One page of a [CaptureSession]: the original photo plus its processed page when ready. */
public data class CapturedPage(
    val id: String,
    val position: Int,
    val originalPath: String,
    val tag: PageTag,
    val pageSize: PageSize,
    val source: PageSource = PageSource.Camera,
    val state: PageState = PageState.Pending,
    val pagePath: String? = null,
    val thumbnailPath: String? = null,
    /** Engine corners in original-photo pixels; null until processed. */
    val corners: Quad? = null,
    val confidence: Confidence? = null,
    val rotationApplied: Int = 0,
    /** The look the page is rendered in (spec 003 FR-024); new pages use the enhanced look. */
    val style: OutputStyle = DEFAULT_STYLE,
    /** User rotation, clockwise, 0/90/180/270 (spec 003 FR-029). Applied after the mirror. */
    val rotation: Int = 0,
    /** Mirrored left to right (spec 003 FR-029). */
    val mirrored: Boolean = false,
    /** Bumped on every rotate / flip so a re-render writes new file names. */
    val revision: Int = 0,
) {
    public companion object {
        public val DEFAULT_STYLE: OutputStyle = OutputStyle.ColorDocument
    }

    public val isReady: Boolean get() = state is PageState.Ready
}

/** Why a processed page should be looked at in review. */
public enum class AttentionReason { NoPageFound, TooDark, LowConfidence }

/** Why no page could be produced; the original is kept. */
public enum class PageFailure { Unreadable, OutOfMemory, Internal, Cancelled }

/** `Pending → Processing → Ready | NeedsAttention | Failed` (data-model.md). */
public sealed interface PageState {
    public data object Pending : PageState

    public data object Processing : PageState

    public data object Ready : PageState

    public data class NeedsAttention(
        val reason: AttentionReason,
    ) : PageState

    public data class Failed(
        val reason: PageFailure,
    ) : PageState

    /** True when the page has a usable processed image (ready or merely needing attention). */
    public val hasPage: Boolean
        get() = this is Ready || this is NeedsAttention
}
