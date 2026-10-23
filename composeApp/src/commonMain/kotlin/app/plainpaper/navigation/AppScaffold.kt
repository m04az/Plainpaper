package app.plainpaper.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.plainpaper.composeapp.resources.Res
import app.plainpaper.composeapp.resources.scan_fab_cd
import app.plainpaper.composeapp.resources.tab_documents
import app.plainpaper.composeapp.resources.tab_folders
import app.plainpaper.composeapp.resources.tab_settings
import app.plainpaper.core.designsystem.component.PpFloatingTabBar
import app.plainpaper.core.designsystem.component.PpScanFab
import app.plainpaper.core.designsystem.component.PpTabItem
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import org.jetbrains.compose.resources.stringResource

private val BarBottomPadding = 14.dp

private const val TAB_DOCUMENTS = "documents"
private const val TAB_FOLDERS = "folders"
private const val TAB_SETTINGS = "settings"

/**
 * Draws [content] full-screen and overlays the floating tab bar + Scan FAB on tab destinations.
 */
@Composable
fun AppScaffold(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val tabs =
        listOf(
            PpTabItem(TAB_DOCUMENTS, stringResource(Res.string.tab_documents), PpIcons.Document),
            PpTabItem(TAB_FOLDERS, stringResource(Res.string.tab_folders), PpIcons.Folder),
            PpTabItem(TAB_SETTINGS, stringResource(Res.string.tab_settings), PpIcons.Settings),
        )
    val selectedTab = destination?.selectedTabId()?.let { id -> tabs.firstOrNull { it.id == id } }

    Surface(color = PlainpaperTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            if (selectedTab != null) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = PlainpaperTheme.spacing.l, vertical = BarBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(PlainpaperTheme.spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PpFloatingTabBar(
                        items = tabs,
                        selected = selectedTab,
                        onSelect = { tab -> navController.navigateToTab(tab.id.toRoute()) },
                        modifier = Modifier.weight(1f),
                    )
                    PpScanFab(
                        onClick = { navController.navigate(ScanPlaceholder) },
                        contentDescription = stringResource(Res.string.scan_fab_cd),
                    )
                }
            }
        }
    }
}

private fun NavDestination.selectedTabId(): String? =
    when {
        hasRoute<Home>() -> TAB_DOCUMENTS
        hasRoute<Folders>() -> TAB_FOLDERS
        hasRoute<Settings>() -> TAB_SETTINGS
        else -> null
    }

private fun String.toRoute(): TabRoute =
    when (this) {
        TAB_FOLDERS -> Folders
        TAB_SETTINGS -> Settings
        else -> Home
    }

fun NavHostController.navigateToTab(route: TabRoute) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
