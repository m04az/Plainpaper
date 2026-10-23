package app.plainpaper.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.plainpaper.composeapp.resources.Res
import app.plainpaper.composeapp.resources.placeholder_folders_body
import app.plainpaper.composeapp.resources.placeholder_folders_title
import app.plainpaper.composeapp.resources.placeholder_import_body
import app.plainpaper.composeapp.resources.placeholder_import_title
import app.plainpaper.composeapp.resources.placeholder_scan_body
import app.plainpaper.composeapp.resources.placeholder_scan_title
import app.plainpaper.feature.home.HomeRoute
import app.plainpaper.placeholder.PlaceholderScreen
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeRoute(
                onNavigateToScan = { navController.navigate(ScanPlaceholder) },
                onNavigateToImport = { navController.navigate(ImportPlaceholder) },
            )
        }
        composable<Folders> {
            PlaceholderScreen(
                title = stringResource(Res.string.placeholder_folders_title),
                body = stringResource(Res.string.placeholder_folders_body),
                onBack = { navController.navigateToTab(Home) },
            )
        }
        composable<Settings> {
            // Replaced by SettingsRoute in US2 (T057).
            PlaceholderScreen(title = "", body = "", onBack = null)
        }
        composable<ScanPlaceholder> {
            PlaceholderScreen(
                title = stringResource(Res.string.placeholder_scan_title),
                body = stringResource(Res.string.placeholder_scan_body),
                onBack = { navController.popBackStack() },
            )
        }
        composable<ImportPlaceholder> {
            PlaceholderScreen(
                title = stringResource(Res.string.placeholder_import_title),
                body = stringResource(Res.string.placeholder_import_body),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
