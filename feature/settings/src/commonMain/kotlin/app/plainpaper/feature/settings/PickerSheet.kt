package app.plainpaper.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.feature.settings.resources.Res
import app.plainpaper.feature.settings.resources.selected_cd
import org.jetbrains.compose.resources.stringResource

private val OptionMinHeight = 52.dp
private val CheckSize = 20.dp

internal data class PickerOption<T>(
    val value: T,
    val label: String,
)

/** Bottom sheet with a title and single-choice rows (radio semantics, check on the selected one). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> PickerSheet(
    title: String,
    options: List<PickerOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = PlainpaperTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = PlainpaperTheme.shapes.sheet,
        containerColor = PlainpaperTheme.colors.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = spacing.xxl, end = spacing.xxl, bottom = spacing.xxl),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = PlainpaperTheme.colors.onSurface,
                modifier = Modifier.padding(bottom = spacing.m),
            )
            options.forEach { option ->
                val isSelected = option.value == selected
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = OptionMinHeight)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(option.value) },
                                role = Role.RadioButton,
                            ).padding(vertical = spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PlainpaperTheme.colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = PpIcons.Check,
                            contentDescription = stringResource(Res.string.selected_cd),
                            tint = PlainpaperTheme.colors.primary,
                            modifier = Modifier.size(CheckSize),
                        )
                    } else {
                        Spacer(Modifier.size(CheckSize))
                    }
                }
            }
        }
    }
}
