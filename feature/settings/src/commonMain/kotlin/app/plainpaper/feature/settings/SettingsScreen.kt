package app.plainpaper.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plainpaper.core.designsystem.component.PpSettingsDivider
import app.plainpaper.core.designsystem.component.PpSettingsGroup
import app.plainpaper.core.designsystem.component.PpSettingsRow
import app.plainpaper.core.designsystem.component.PpTopTitle
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.ThemeMode
import app.plainpaper.feature.settings.resources.Res
import app.plainpaper.feature.settings.resources.settings_appearance
import app.plainpaper.feature.settings.resources.settings_group_about
import app.plainpaper.feature.settings.resources.settings_group_app
import app.plainpaper.feature.settings.resources.settings_language
import app.plainpaper.feature.settings.resources.settings_privacy
import app.plainpaper.feature.settings.resources.settings_privacy_value
import app.plainpaper.feature.settings.resources.settings_title
import app.plainpaper.feature.settings.resources.settings_version
import app.plainpaper.feature.settings.resources.sheet_language_title
import app.plainpaper.feature.settings.resources.sheet_theme_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Space reserved at the bottom so content clears the floating tab bar. */
private val BottomBarClearance = 120.dp

@Composable
fun SettingsRoute() {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PlainpaperTheme.spacing
    val colors = PlainpaperTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(top = spacing.xl, bottom = BottomBarClearance),
    ) {
        PpTopTitle(text = stringResource(Res.string.settings_title))

        PpSettingsGroup(title = stringResource(Res.string.settings_group_app)) {
            PpSettingsRow(
                title = stringResource(Res.string.settings_language),
                icon = PpIcons.Globe,
                iconTint = colors.primary,
                value = state.language.label(),
                onClick = { onAction(SettingsAction.LanguageRowClicked) },
            )
            PpSettingsDivider()
            PpSettingsRow(
                title = stringResource(Res.string.settings_appearance),
                icon = PpIcons.Moon,
                iconTint = colors.onSurfaceVariant,
                value = state.themeMode.label(),
                onClick = { onAction(SettingsAction.ThemeRowClicked) },
            )
        }

        PpSettingsGroup(title = stringResource(Res.string.settings_group_about)) {
            PpSettingsRow(
                title = stringResource(Res.string.settings_privacy),
                icon = PpIcons.Shield,
                iconTint = PlainpaperTheme.extended.success,
                value = stringResource(Res.string.settings_privacy_value),
            )
            PpSettingsDivider()
            PpSettingsRow(
                title = stringResource(Res.string.settings_version),
                icon = PpIcons.Info,
                iconTint = colors.outline,
                value = state.appVersion,
            )
        }
    }

    when (state.openSheet) {
        SettingsSheet.LANGUAGE -> {
            PickerSheet(
                title = stringResource(Res.string.sheet_language_title),
                options = AppLanguage.entries.map { PickerOption(it, it.label()) },
                selected = state.language,
                onSelect = { onAction(SettingsAction.LanguageSelected(it)) },
                onDismiss = { onAction(SettingsAction.SheetDismissed) },
            )
        }

        SettingsSheet.THEME -> {
            PickerSheet(
                title = stringResource(Res.string.sheet_theme_title),
                options = ThemeMode.entries.map { PickerOption(it, it.label()) },
                selected = state.themeMode,
                onSelect = { onAction(SettingsAction.ThemeSelected(it)) },
                onDismiss = { onAction(SettingsAction.SheetDismissed) },
            )
        }

        null -> {
            Unit
        }
    }
}
