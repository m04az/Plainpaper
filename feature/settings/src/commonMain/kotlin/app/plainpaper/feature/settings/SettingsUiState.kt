package app.plainpaper.feature.settings

import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.ThemeMode

data class SettingsUiState(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val openSheet: SettingsSheet? = null,
    val appVersion: String = "",
)

enum class SettingsSheet {
    LANGUAGE,
    THEME,
}
