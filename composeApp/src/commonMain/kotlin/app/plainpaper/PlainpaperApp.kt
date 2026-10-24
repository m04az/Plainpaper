package app.plainpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.usecase.ObservePreferencesUseCase
import app.plainpaper.feature.capture.CaptureResume
import app.plainpaper.locale.AppEnvironment
import app.plainpaper.locale.systemLanguageTag
import app.plainpaper.navigation.AppNavHost
import app.plainpaper.navigation.AppScaffold
import org.koin.compose.koinInject

/** Root of the shared UI. Hosted by `MainActivity` on Android and `MainViewController` on iOS. */
@Composable
fun PlainpaperApp() {
    // Created above AppEnvironment so re-keying on language never resets the back stack.
    val navController = rememberNavController()
    val observePreferences: ObservePreferencesUseCase = koinInject()
    val preferences: AppPreferences? by observePreferences().collectAsStateWithLifecycle(initialValue = null)
    val loaded = preferences

    if (loaded == null) {
        // One blank frame on the paper background instead of a flash of the wrong language/theme.
        PlainpaperTheme { Box(Modifier.fillMaxSize().background(PlainpaperTheme.colors.background)) }
        return
    }

    val shell = loaded.toShellState(systemTag = systemLanguageTag(), systemDark = isSystemInDarkTheme())

    SystemBarsAppearance(isDark = shell.isDark)
    CaptureResume()
    PlainpaperTheme(darkTheme = shell.isDark, useArabicTypeface = shell.useArabicTypeface) {
        AppEnvironment(language = shell.language, layoutDirection = shell.layoutDirection) {
            AppScaffold(navController = navController) {
                AppNavHost(navController = navController)
            }
        }
    }
}
