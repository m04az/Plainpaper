package app.plainpaper.feature.settings

import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.ThemeMode

sealed interface SettingsAction {
    data object LanguageRowClicked : SettingsAction

    data object ThemeRowClicked : SettingsAction

    data class LanguageSelected(
        val language: AppLanguage,
    ) : SettingsAction

    data class ThemeSelected(
        val mode: ThemeMode,
    ) : SettingsAction

    data object SheetDismissed : SettingsAction
}
