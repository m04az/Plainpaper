package app.plainpaper.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.plainpaper.composeapp.resources.Res
import app.plainpaper.composeapp.resources.placeholder_folders_body
import app.plainpaper.composeapp.resources.placeholder_folders_title
import app.plainpaper.composeapp.resources.placeholder_pro_body
import app.plainpaper.composeapp.resources.placeholder_pro_title
import app.plainpaper.feature.capture.CameraRoute
import app.plainpaper.feature.capture.HandoffRoute
import app.plainpaper.feature.capture.ImportRoute
import app.plainpaper.feature.capture.camera.CameraRoute
import app.plainpaper.feature.capture.handoff.HandoffRoute
import app.plainpaper.feature.capture.import.ImportRoute
import app.plainpaper.feature.home.HomeRoute
import app.plainpaper.feature.settings.SettingsRoute
import app.plainpaper.placeholder.PlaceholderScreen
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeRoute(
                onNavigateToScan = { navController.navigate(CameraRoute) },
                onNavigateToImport = { navController.navigate(ImportRoute) },
                onNavigateToHandoff = { sessionId -> navController.navigate(HandoffRoute(sessionId)) },
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
            SettingsRoute()
        }
        composable<CameraRoute> {
            CameraRoute(
                onBack = { navController.popBackStack() },
                onHandoff = { sessionId ->
                    navController.navigate(HandoffRoute(sessionId)) {
                        popUpTo<CameraRoute> {
                            inclusive =
                                true
                        }
                    }
                },
                onImport = { navController.navigate(ImportRoute) { popUpTo<CameraRoute> { inclusive = true } } },
            )
        }
        composable<HandoffRoute> { entry ->
            HandoffRoute(
                sessionId = entry.toRoute<HandoffRoute>().sessionId,
                onHome = { navController.navigateToTab(Home) },
            )
        }
        composable<ImportRoute> {
            ImportRoute(
                onBack = { navController.popBackStack() },
                onHome = { navController.navigateToTab(Home) },
                onHandoff = { sessionId ->
                    navController.navigate(HandoffRoute(sessionId)) {
                        popUpTo<ImportRoute> {
                            inclusive =
                                true
                        }
                    }
                },
                onPro = { navController.navigate(ProPlaceholder) },
            )
        }
        composable<ProPlaceholder> {
            PlaceholderScreen(
                title = stringResource(Res.string.placeholder_pro_title),
                body = stringResource(Res.string.placeholder_pro_body),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
