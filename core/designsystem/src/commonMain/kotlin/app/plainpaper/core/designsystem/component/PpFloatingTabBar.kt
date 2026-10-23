package app.plainpaper.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.theme.PlainpaperTheme

private val BarHeight = 62.dp
private val ItemIconSize = 22.dp
private val ItemMinWidth = 64.dp

@Immutable
public data class PpTabItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Floating capsule tab bar on the glass surface; the selected item is tinted primary inside a
 * primaryContainer pill. Items are laid out in a Row, so they mirror automatically in RTL.
 */
@Composable
public fun PpFloatingTabBar(
    items: List<PpTabItem>,
    selected: PpTabItem?,
    onSelect: (PpTabItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(BarHeight),
        shape = PlainpaperTheme.shapes.capsule,
        color = PlainpaperTheme.extended.glassSurface,
        border = BorderStroke(1.dp, PlainpaperTheme.extended.glassStroke),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PlainpaperTheme.spacing.s),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                TabBarItem(
                    item = item,
                    selected = item == selected,
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun TabBarItem(
    item: PpTabItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) PlainpaperTheme.colors.primary else PlainpaperTheme.colors.onSurfaceVariant
    val pill = if (selected) PlainpaperTheme.colors.primaryContainer else Color.Transparent
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .widthIn(min = ItemMinWidth)
                .padding(vertical = PlainpaperTheme.spacing.s)
                .background(pill, PlainpaperTheme.shapes.capsule)
                .selectable(selected = selected, onClick = onClick, role = Role.Tab)
                .padding(horizontal = PlainpaperTheme.spacing.m, vertical = PlainpaperTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ItemIconSize),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
