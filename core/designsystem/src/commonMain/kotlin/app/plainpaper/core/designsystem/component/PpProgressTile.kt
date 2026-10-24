package app.plainpaper.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme

private val TileHeight = 98.dp
private val PhotoWidth = 56.dp
private val PhotoHeight = 72.dp
private val BadgeSize = 20.dp
private val BadgeIcon = 12.dp
private val ActiveRing = 2.5.dp

public enum class PpTileState { Queued, Active, Done, Skipped }

/**
 * One import tile (design "Import progress"): queued photos sit tilted, the active one has a
 * primary ring, done pages show the clean thumbnail with a green tick, skipped ones a warning.
 */
@Composable
public fun PpProgressTile(
    state: PpTileState,
    thumbnail: ImageBitmap?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tiltDegrees: Float = 0f,
) {
    val colors = PlainpaperTheme.colors
    Box(
        modifier =
            modifier
                .height(TileHeight)
                .clip(PlainpaperTheme.shapes.search)
                .background(colors.surfaceVariant)
                .then(
                    if (state ==
                        PpTileState.Active
                    ) {
                        Modifier.border(ActiveRing, colors.primary, PlainpaperTheme.shapes.search)
                    } else {
                        Modifier
                    },
                ).semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(PhotoWidth, PhotoHeight)
                    .rotate(if (state == PpTileState.Done) 0f else tiltDegrees)
                    .clip(PlainpaperTheme.shapes.thumbnail)
                    .background(if (state == PpTileState.Done) PlainpaperTheme.extended.paper else colors.outline),
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        when (state) {
            PpTileState.Done -> {
                Badge(
                    PlainpaperTheme.extended.success,
                    PpIcons.Check,
                    Modifier.align(Alignment.BottomEnd),
                )
            }

            PpTileState.Skipped -> {
                Badge(colors.error, PpIcons.Warning, Modifier.align(Alignment.BottomEnd))
            }

            else -> {
                Unit
            }
        }
    }
}

@Composable
private fun Badge(
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.padding(5.dp).size(BadgeSize).background(color, PlainpaperTheme.shapes.capsule),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PlainpaperTheme.colors.surface,
            modifier = Modifier.size(BadgeIcon),
        )
    }
}
