package app.plainpaper.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme

private val FabSize = 62.dp
private val FabIconSize = 26.dp

/** The always-available round Scan action beside the tab bar. */
@Composable
public fun PpScanFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(FabSize),
        shape = PlainpaperTheme.shapes.capsule,
        containerColor = PlainpaperTheme.colors.primary,
        contentColor = PlainpaperTheme.colors.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
    ) {
        Icon(
            imageVector = PpIcons.ScanFrame,
            contentDescription = contentDescription,
            modifier = Modifier.size(FabIconSize),
        )
    }
}
