package app.plainpaper.core.domain.model.capture

import app.plainpaper.core.domain.model.PageSize

/** Camera modes offered on the capture screen (spec FR-016). */
public enum class CaptureMode {
    Document,
    IdCard,
    Receipt,
    ;

    /** Receipt and ID card pages keep their own proportions; documents use the region default. */
    public fun resolvePageSize(regionDefault: PageSize): PageSize =
        when (this) {
            Document -> regionDefault
            IdCard, Receipt -> PageSize.Natural
        }
}

/** What a page is, recorded for the review and the ID card / receipt features. */
public sealed interface PageTag {
    public data object Document : PageTag

    public data class IdFront(
        val cardId: String,
    ) : PageTag

    public data class IdBack(
        val cardId: String,
    ) : PageTag

    public data object Receipt : PageTag
}

/** Camera choices remembered between sessions (spec FR-005). */
public data class CaptureSettings(
    val flash: Boolean = false,
    val autoCapture: Boolean = true,
    val mode: CaptureMode = CaptureMode.Document,
)

/** Camera permission as seen by the app (spec FR-017/FR-018). */
public enum class CameraPermissionState {
    NotAsked,
    Granted,
    Denied,

    /** The system will not prompt again; only Settings can grant it. */
    DeniedPermanently,

    /** The device has no camera. */
    Unavailable,
}
