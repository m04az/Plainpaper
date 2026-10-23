package app.plainpaper.feature.home

sealed interface HomeEffect {
    data object NavigateToScan : HomeEffect

    data object NavigateToImport : HomeEffect
}
