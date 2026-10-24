package app.plainpaper.feature.home

/** A capture session left over from a previous run (spec 003 FR-011). */
data class RecoverableSession(
    val id: String,
    val pageCount: Int,
)

/** Home has no documents until the Document library feature; the empty state is always shown. */
data class HomeUiState(
    val isEmpty: Boolean = true,
    val recoverable: RecoverableSession? = null,
)
