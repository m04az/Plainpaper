package app.plainpaper.feature.home

/** Home has no documents until the Document library feature; the empty state is always shown. */
data class HomeUiState(
    val isEmpty: Boolean = true,
)
