package app.plainpaper.feature.home

sealed interface HomeEffect {
    data object NavigateToScan : HomeEffect

    data object NavigateToImport : HomeEffect

    data class NavigateToHandoff(
        val sessionId: String,
    ) : HomeEffect
}
