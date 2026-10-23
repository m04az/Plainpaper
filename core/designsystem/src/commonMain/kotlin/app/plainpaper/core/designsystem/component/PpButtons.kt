package app.plainpaper.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.theme.PlainpaperTheme

private val ButtonHeight = 52.dp
private val IconSize = 20.dp
private val MinTouchWidth = 48.dp

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
