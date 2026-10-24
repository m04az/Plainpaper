package app.plainpaper.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.theme.PlainpaperTheme

private val ButtonHeight = 52.dp
private val IconSize = 20.dp
private val MinTouchWidth = 48.dp
private val ToolMinHeight = 56.dp
private val ToolIconSize = 24.dp
private const val DISABLED_ALPHA = 0.38f

/** Primary capsule action: ink-blue background, 17sp/600 label, optional leading icon. */
@Composable
public fun PpPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(ButtonHeight).widthIn(min = MinTouchWidth),
        enabled = enabled,
        shape = PlainpaperTheme.shapes.capsule,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = PlainpaperTheme.colors.primary,
                contentColor = PlainpaperTheme.colors.onPrimary,
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp),
    ) {
        ButtonContent(text = text, leadingIcon = leadingIcon)
    }
}

/** Secondary capsule action on the translucent "glass" surface with a hairline stroke. */
@Composable
public fun PpGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(ButtonHeight).widthIn(min = MinTouchWidth),
        enabled = enabled,
        shape = PlainpaperTheme.shapes.capsule,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = PlainpaperTheme.extended.glassSurface,
                contentColor = PlainpaperTheme.colors.onSurface,
            ),
        border = BorderStroke(1.dp, PlainpaperTheme.extended.glassStroke),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
    ) {
        ButtonContent(text = text, leadingIcon = leadingIcon)
    }
}

/** Icon above a short label, for a row of page tools (rotate, flip, retake). */
@Composable
public fun PpToolButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tint =
        PlainpaperTheme.colors.onSurface.let { if (enabled) it else it.copy(alpha = DISABLED_ALPHA) }
    Column(
        modifier =
            modifier
                .heightIn(min = ToolMinHeight)
                .clip(PlainpaperTheme.shapes.capsule)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = PlainpaperTheme.spacing.m, vertical = PlainpaperTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(ToolIconSize))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun RowScope.ButtonContent(
    text: String,
    leadingIcon: ImageVector?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlainpaperTheme.spacing.s),
    ) {
        if (leadingIcon != null) {
            Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(IconSize))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
