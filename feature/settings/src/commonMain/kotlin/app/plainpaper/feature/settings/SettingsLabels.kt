package app.plainpaper.feature.settings

import androidx.compose.runtime.Composable
import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.ThemeMode
import app.plainpaper.feature.settings.resources.Res
import app.plainpaper.feature.settings.resources.language_ar
import app.plainpaper.feature.settings.resources.language_en
import app.plainpaper.feature.settings.resources.language_system
import app.plainpaper.feature.settings.resources.theme_dark
import app.plainpaper.feature.settings.resources.theme_light
import app.plainpaper.feature.settings.resources.theme_system
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppLanguage.label(): String =
    when (this) {
        AppLanguage.SYSTEM -> stringResource(Res.string.language_system)
        AppLanguage.ENGLISH -> stringResource(Res.string.language_en)
        AppLanguage.ARABIC -> stringResource(Res.string.language_ar)
    }

@Composable
internal fun ThemeMode.label(): String =
    when (this) {
        ThemeMode.SYSTEM -> stringResource(Res.string.theme_system)
        ThemeMode.LIGHT -> stringResource(Res.string.theme_light)
        ThemeMode.DARK -> stringResource(Res.string.theme_dark)
    }
