package app.plainpaper.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.plainpaper.core.designsystem.theme.PlainpaperTheme

/** Centered illustration, title, body and two stacked actions (design "Home — empty"). */
@Composable
public fun PpEmptyState(
    title: String,
    body: String,
    illustration: @Composable () -> Unit,
    primaryAction: @Composable () -> Unit,
    secondaryAction: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PlainpaperTheme.spacing
    Column(
        modifier = modifier.padding(horizontal = spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        illustration()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = PlainpaperTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = PlainpaperTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.s + spacing.xs / 2),
        ) {
            primaryAction()
            secondaryAction()
        }
    }
}
