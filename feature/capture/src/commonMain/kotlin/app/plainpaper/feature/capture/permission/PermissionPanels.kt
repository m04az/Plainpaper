package app.plainpaper.feature.capture.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.component.PpGlassButton
import app.plainpaper.core.designsystem.component.PpPrimaryButton
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.designsystem.theme.PpColors
import app.plainpaper.feature.capture.resources.Res
import app.plainpaper.feature.capture.resources.permission_allow
import app.plainpaper.feature.capture.resources.permission_denied_body
import app.plainpaper.feature.capture.resources.permission_denied_title
import app.plainpaper.feature.capture.resources.permission_explain_body
import app.plainpaper.feature.capture.resources.permission_explain_title
import app.plainpaper.feature.capture.resources.permission_import_instead
import app.plainpaper.feature.capture.resources.permission_open_settings
import org.jetbrains.compose.resources.stringResource

private val IconSize = 56.dp

/** First-time explanation shown before the system prompt (spec FR-017). */
@Composable
internal fun PermissionExplanationPanel(
    onAllow: () -> Unit,
    onImportInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PermissionPanel(
        title = stringResource(Res.string.permission_explain_title),
        body = stringResource(Res.string.permission_explain_body),
        primaryText = stringResource(Res.string.permission_allow),
        onPrimary = onAllow,
        onImportInstead = onImportInstead,
        modifier = modifier,
    )
}

/** Denied: retry the prompt while the system allows it, otherwise open Settings (spec FR-018). */
@Composable
internal fun PermissionDeniedPanel(
    canPromptAgain: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PermissionPanel(
        title = stringResource(Res.string.permission_denied_title),
        body = stringResource(Res.string.permission_denied_body),
        primaryText =
            if (canPromptAgain) {
                stringResource(
                    Res.string.permission_allow,
                )
            } else {
                stringResource(Res.string.permission_open_settings)
            },
        onPrimary = if (canPromptAgain) onRetry else onOpenSettings,
        onImportInstead = onImportInstead,
        modifier = modifier,
    )
}

@Composable
private fun PermissionPanel(
    title: String,
    body: String,
    primaryText: String,
    onPrimary: () -> Unit,
    onImportInstead: () -> Unit,
    modifier: Modifier,
) {
    val spacing = PlainpaperTheme.spacing
    Column(
        modifier = modifier.fillMaxSize().background(PpColors.DarkBackground).padding(horizontal = spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = PpIcons.Lock,
            contentDescription = null,
            tint = PpColors.DarkPrimary,
            modifier = Modifier.size(IconSize),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = PpColors.DarkInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xl),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = PpColors.DarkInkVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.s, bottom = spacing.xxl),
        )
        PpPrimaryButton(text = primaryText, onClick = onPrimary, modifier = Modifier.fillMaxWidth())
        PpGlassButton(
            text = stringResource(Res.string.permission_import_instead),
            onClick = onImportInstead,
            leadingIcon = PpIcons.ImportPhoto,
            modifier = Modifier.fillMaxWidth().padding(top = spacing.m),
        )
    }
}
