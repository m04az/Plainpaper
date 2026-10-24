package app.plainpaper.feature.capture

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.feature.capture.resources.Res
import app.plainpaper.feature.capture.resources.handoff_look_bw
import app.plainpaper.feature.capture.resources.handoff_look_enhanced
import app.plainpaper.feature.capture.resources.handoff_look_grayscale
import app.plainpaper.feature.capture.resources.handoff_look_original
import app.plainpaper.feature.capture.resources.handoff_looks_cd
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The four looks in the order shown (spec 003 US6). */
private val Looks: List<Pair<OutputStyle, StringResource>> =
    listOf(
        OutputStyle.Original to Res.string.handoff_look_original,
        OutputStyle.ColorDocument to Res.string.handoff_look_enhanced,
        OutputStyle.Grayscale to Res.string.handoff_look_grayscale,
        OutputStyle.Scan to Res.string.handoff_look_bw,
    )

/** Horizontally scrolling row of look chips shared by the camera review and the page viewer. */
@Composable
internal fun LookChipRow(
    selected: OutputStyle?,
    enabled: Boolean,
    onSelect: (OutputStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PlainpaperTheme.spacing
    val description = stringResource(Res.string.handoff_looks_cd)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = spacing.l)
                .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Looks.forEach { (style, label) ->
            FilterChip(
                selected = selected == style,
                onClick = { onSelect(style) },
                enabled = enabled,
                label = { Text(stringResource(label)) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PlainpaperTheme.colors.primaryContainer,
                        selectedLabelColor = PlainpaperTheme.colors.onPrimaryContainer,
                    ),
            )
        }
    }
}
