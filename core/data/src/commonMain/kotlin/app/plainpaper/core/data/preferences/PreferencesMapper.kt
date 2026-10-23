package app.plainpaper.core.data.preferences

import androidx.datastore.preferences.core.Preferences
import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.model.ThemeMode

private const val TOKEN_SYSTEM = "system"
private const val TOKEN_ENGLISH = "en"
private const val TOKEN_ARABIC = "ar"
private const val TOKEN_LIGHT = "light"
private const val TOKEN_DARK = "dark"

internal val AppLanguage.token: String
    get() =
        when (this) {
            AppLanguage.SYSTEM -> TOKEN_SYSTEM
            AppLanguage.ENGLISH -> TOKEN_ENGLISH
            AppLanguage.ARABIC -> TOKEN_ARABIC
        }

internal val ThemeMode.token: String
    get() =
        when (this) {
            ThemeMode.SYSTEM -> TOKEN_SYSTEM
            ThemeMode.LIGHT -> TOKEN_LIGHT
            ThemeMode.DARK -> TOKEN_DARK
        }

/** Unknown or missing tokens map to the default and never throw (contracts/preferences.md). */
internal fun String?.toAppLanguage(): AppLanguage =
    when (this) {
        TOKEN_ENGLISH -> AppLanguage.ENGLISH
        TOKEN_ARABIC -> AppLanguage.ARABIC
        else -> AppLanguage.SYSTEM
    }

internal fun String?.toThemeMode(): ThemeMode =
    when (this) {
        TOKEN_LIGHT -> ThemeMode.LIGHT
        TOKEN_DARK -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

internal fun Preferences.toAppPreferences(): AppPreferences =
    AppPreferences(
        language = this[PreferencesKeys.LANGUAGE].toAppLanguage(),
        themeMode = this[PreferencesKeys.THEME].toThemeMode(),
    )
