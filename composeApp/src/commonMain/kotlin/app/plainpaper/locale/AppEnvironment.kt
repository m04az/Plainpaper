package app.plainpaper.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.AppLayoutDirection

/**
 * Applies the chosen language to resources and the layout direction to every descendant.
 * Content is re-keyed on [language] so every `stringResource` re-reads with the new locale.
 * The NavHostController MUST be created above this composable (contracts/ui-contracts.md §5).
 */
@Composable
fun AppEnvironment(
    language: AppLanguage,
    layoutDirection: AppLayoutDirection,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppLocale provides language.tag,
        LocalLayoutDirection provides layoutDirection.toCompose(),
    ) {
        key(language) {
            content()
        }
    }
}

fun AppLayoutDirection.toCompose(): LayoutDirection =
    when (this) {
        AppLayoutDirection.LTR -> LayoutDirection.Ltr
        AppLayoutDirection.RTL -> LayoutDirection.Rtl
    }
