package app.plainpaper

import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.AppLayoutDirection
import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.model.ThemeMode
import app.plainpaper.core.domain.model.resolveIsDark
import app.plainpaper.core.domain.model.resolveLayoutDirection

/** Everything the app shell derives from preferences plus the device state. */
data class AppShellState(
    val language: AppLanguage,
    val themeMode: ThemeMode,
    val layoutDirection: AppLayoutDirection,
    val isDark: Boolean,
) {
    val useArabicTypeface: Boolean get() = layoutDirection == AppLayoutDirection.RTL
}

fun AppPreferences.toShellState(
    systemTag: String?,
    systemDark: Boolean,
): AppShellState =
    AppShellState(
        language = language,
        themeMode = themeMode,
        layoutDirection = resolveLayoutDirection(language, systemTag),
        isDark = themeMode.resolveIsDark(systemDark),
    )
