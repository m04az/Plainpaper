package app.plainpaper

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.resolveLayoutDirection
import app.plainpaper.locale.AppEnvironment
import app.plainpaper.locale.systemLanguageTag
import app.plainpaper.navigation.AppNavHost
import app.plainpaper.navigation.AppScaffold

/** Root of the shared UI. Hosted by `MainActivity` on Android and `MainViewController` on iOS. */
@Composable
fun PlainpaperApp() {
    // Created above AppEnvironment so re-keying on language never resets the back stack.
    val navController = rememberNavController()
    val language = AppLanguage.SYSTEM
    val layoutDirection = resolveLayoutDirection(language, systemLanguageTag())

    PlainpaperTheme {
        AppEnvironment(language = language, layoutDirection = layoutDirection) {
            AppScaffold(navController = navController) {
                AppNavHost(navController = navController)
            }
        }
    }
}
