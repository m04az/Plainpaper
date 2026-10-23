package app.plainpaper.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.plainpaper.composeapp.resources.Res
import app.plainpaper.composeapp.resources.back_cd
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import org.jetbrains.compose.resources.stringResource

private val BackButtonSize = 48.dp

/**
 * Stateless informational screen for capabilities that arrive in later features:
 * a back arrow (mirrored in RTL), a title and a one-line body.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    body: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = PlainpaperTheme.spacing
    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.s, vertical = spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(BackButtonSize)) {
                    Icon(
                        imageVector = PpIcons.ArrowBack,
                        contentDescription = stringResource(Res.string.back_cd),
                        tint = PlainpaperTheme.colors.onSurface,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = PlainpaperTheme.colors.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = PlainpaperTheme.colors.onSurfaceVariant,
            )
        }
    }
}
