package app.plainpaper.feature.home

sealed interface HomeAction {
    data object ScanClicked : HomeAction

    data object ImportClicked : HomeAction
}
