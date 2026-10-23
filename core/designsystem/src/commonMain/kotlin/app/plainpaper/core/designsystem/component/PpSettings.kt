package app.plainpaper.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme

private val RowMinHeight = 50.dp
private val IconBoxSize = 30.dp
private val IconSize = 16.dp
private val ChevronSize = 12.dp

/** Uppercase caption plus a surface card holding [PpSettingsRow]s separated by hairlines. */
@Composable
public fun PpSettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = PlainpaperTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = PlainpaperTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(start = spacing.xxxl, end = spacing.xxxl, top = spacing.xl, bottom = spacing.s),
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l),
            shape = PlainpaperTheme.shapes.card,
            color = PlainpaperTheme.colors.surface,
        ) {
            Column(content = content)
        }
    }
}

/** Hairline between rows inside a [PpSettingsGroup]. */
@Composable
public fun PpSettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = PlainpaperTheme.spacing.l),
        color = PlainpaperTheme.colors.outline,
    )
}

/**
 * One settings row: tinted icon box, title, trailing value and (when clickable) an
 * auto-mirrored chevron. Minimum height keeps the touch target above 48dp.
 */
@Composable
public fun PpSettingsRow(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val spacing = PlainpaperTheme.spacing
    val clickModifier =
        if (onClick != null) {
            Modifier.clickable(onClick = onClick, role = Role.Button)
        } else {
            Modifier
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(clickModifier)
                .heightIn(min = RowMinHeight)
                .padding(horizontal = spacing.l, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Box(
            modifier = Modifier.size(IconBoxSize).background(iconTint, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PlainpaperTheme.colors.surface,
                modifier = Modifier.size(IconSize),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = PlainpaperTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = PlainpaperTheme.colors.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            trailing()
        }
        if (onClick != null) {
            Spacer(Modifier.width(spacing.xs / 2))
            Icon(
                imageVector = PpIcons.ChevronEnd,
                contentDescription = null,
                tint = PlainpaperTheme.colors.outline,
                modifier = Modifier.size(ChevronSize),
            )
        }
    }
}
