package app.plainpaper.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.designsystem.theme.PpColors

private val GlassButtonSize = 44.dp
private val GlassIconSize = 18.dp
private val PillHeight = 44.dp
private val ChipHeight = 34.dp
private val ChipIconSize = 16.dp
private val SegmentHeight = 36.dp
private val ShutterSize = 78.dp
private val ShutterRing = 4.dp
private val ShutterGap = 4.dp

/** Dark translucent glass used by every control drawn over the live preview (design "Camera"). */
@Composable
public fun PpCameraGlass(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = PlainpaperTheme.shapes.capsule,
        color = PpColors.CameraGlass,
        contentColor = PpColors.CameraOnGlass,
        border = BorderStroke(1.dp, PpColors.CameraGlassStroke),
    ) {
        content()
    }
}

/** 44 dp round glass icon button (close camera); the touch target is 48 dp. */
@Composable
public fun PpGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .size(GlassButtonSize)
                .border(1.dp, PpColors.CameraGlassStroke, PlainpaperTheme.shapes.capsule),
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = PpColors.CameraGlass,
                contentColor = PpColors.CameraOnGlass,
            ),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(GlassIconSize))
    }
}

/** Glass pill that holds [PpToggleChip]s (flash, Auto). */
@Composable
public fun PpGlassPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    PpCameraGlass(modifier = modifier.height(PillHeight)) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** On/off chip inside a [PpGlassPill]: white when selected, outlined text otherwise. */
@Composable
public fun PpToggleChip(
    text: String,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    iconTint: Color? = null,
) {
    val background = if (selected) PpColors.CameraOnGlass else Color.Transparent
    val content = if (selected) PpColors.LightInk else PpColors.CameraOnGlass
    Row(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .height(ChipHeight)
                .background(background, PlainpaperTheme.shapes.capsule)
                .toggleable(value = selected, onValueChange = onToggle, role = Role.Switch)
                .semantics { this.contentDescription = contentDescription }
                .padding(horizontal = PlainpaperTheme.spacing.m),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconTint ?: content,
                modifier = Modifier.size(ChipIconSize),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Immutable
public data class PpSegment(
    val id: String,
    val label: String,
)

/** Glass segmented control (scan modes). Items are laid out in a Row and mirror in RTL. */
@Composable
public fun PpSegmentedControl(
    segments: List<PpSegment>,
    selectedId: String,
    onSelect: (PpSegment) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    PpCameraGlass(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEach { segment ->
                val selected = segment.id == selectedId
                val background = if (selected) PpColors.CameraOnGlass else Color.Transparent
                val content = if (selected) PpColors.LightInk else PpColors.CameraOnGlass
                Box(
                    modifier =
                        Modifier
                            .minimumInteractiveComponentSize()
                            .height(SegmentHeight)
                            .background(background, PlainpaperTheme.shapes.capsule)
                            .selectable(selected = selected, onClick = { onSelect(segment) }, role = Role.Tab)
                            .padding(horizontal = PlainpaperTheme.spacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = segment.label, style = MaterialTheme.typography.labelLarge, color = content)
                }
            }
        }
    }
}

/** The 78 dp white shutter: a ring with an inner disc. */
@Composable
public fun PpShutterButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .size(ShutterSize)
                .border(ShutterRing, PpColors.CameraOnGlass, PlainpaperTheme.shapes.capsule)
                .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier =
                Modifier
                    .size(ShutterSize - (ShutterRing + ShutterGap) * 2)
                    .background(PpColors.CameraOnGlass, PlainpaperTheme.shapes.capsule),
        )
    }
}

/** Primary capsule on the camera (Done): same colours as [PpPrimaryButton], 44 dp tall. */
@Composable
public fun PpCameraPrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(PillHeight),
        shape = PlainpaperTheme.shapes.capsule,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = PpColors.LightPrimary,
                contentColor = PpColors.LightOnPrimary,
            ),
        contentPadding = PaddingValues(horizontal = PlainpaperTheme.spacing.l),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
