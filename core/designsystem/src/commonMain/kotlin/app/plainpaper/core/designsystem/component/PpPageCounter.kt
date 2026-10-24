package app.plainpaper.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.designsystem.theme.PpColors

private val ThumbWidth = 52.dp
private val ThumbHeight = 64.dp
private val BadgeSize = 22.dp
private val BadgeOffset = 8.dp

/** Latest page thumbnail with the page-count badge (bottom-left of the camera). */
@Composable
public fun PpPageCounter(
    count: Int,
    thumbnail: ImageBitmap?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.size(ThumbWidth + BadgeOffset, ThumbHeight + BadgeOffset).semantics {
                this.contentDescription =
                    contentDescription
            },
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .size(ThumbWidth, ThumbHeight)
                    .shadow(6.dp, PlainpaperTheme.shapes.thumbnail)
                    .clip(PlainpaperTheme.shapes.thumbnail)
                    .background(PpColors.Paper),
        ) {
            if (thumbnail != null) {
                Image(bitmap = thumbnail, contentDescription = null, contentScale = ContentScale.Crop)
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = BadgeSize)
                    .height(BadgeSize)
                    .background(PpColors.LightPrimary, PlainpaperTheme.shapes.capsule)
                    .padding(horizontal = 6.dp)
                    .offset(x = 0.dp, y = 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = count.toString(), style = MaterialTheme.typography.labelSmall, color = PpColors.LightOnPrimary)
        }
    }
}
