package app.plainpaper.feature.home

sealed interface HomeAction {
    data object ScanClicked : HomeAction

    data object ImportClicked : HomeAction

    /** The screen became visible again: re-check for a recoverable capture session. */
    data object ScreenStarted : HomeAction

    data object ContinueRecovery : HomeAction

    data object DiscardRecovery : HomeAction
}
