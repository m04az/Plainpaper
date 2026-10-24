package app.plainpaper.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.designsystem.theme.PpColors

private val DotSize = 8.dp

public enum class PpHintTone { Neutral, Ready, Warning }

/** Camera guidance pill: a coloured dot plus one line of text, announced politely. */
@Composable
public fun PpHintPill(
    text: String,
    tone: PpHintTone,
    modifier: Modifier = Modifier,
) {
    val dot =
        when (tone) {
            PpHintTone.Neutral -> PpColors.CameraOnGlass
            PpHintTone.Ready -> PpColors.CameraReady
            PpHintTone.Warning -> PpColors.CameraWarning
        }
    PpCameraGlass(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(
            modifier = Modifier.padding(horizontal = PlainpaperTheme.spacing.l, vertical = PlainpaperTheme.spacing.s),
            horizontalArrangement = Arrangement.spacedBy(PlainpaperTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(DotSize).background(dot, PlainpaperTheme.shapes.capsule))
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
