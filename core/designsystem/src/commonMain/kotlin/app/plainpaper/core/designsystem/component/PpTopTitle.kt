package app.plainpaper.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.plainpaper.core.designsystem.theme.PlainpaperTheme

/** Large start-aligned screen title (34sp / 800), announced as a heading. */
@Composable
public fun PpTopTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall,
        color = PlainpaperTheme.colors.onSurface,
        modifier =
            modifier
                .padding(horizontal = PlainpaperTheme.spacing.xl)
                .semantics { heading() },
    )
}
