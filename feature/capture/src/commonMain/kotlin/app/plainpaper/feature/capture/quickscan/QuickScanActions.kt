package app.plainpaper.feature.capture.quickscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plainpaper.core.capture.scanner.DocumentScannerFactory
import app.plainpaper.core.designsystem.component.PpGlassButton
import app.plainpaper.core.designsystem.component.PpHintPill
import app.plainpaper.core.designsystem.component.PpHintTone
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.feature.capture.resources.Res
import app.plainpaper.feature.capture.resources.quickscan_action
import app.plainpaper.feature.capture.resources.quickscan_failed
import app.plainpaper.feature.capture.resources.quickscan_preparing
import app.plainpaper.feature.capture.resources.quickscan_subtitle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** State and tap handler of one Quick scan entry point; [state] is hidden until the phone answers. */
@Stable
private class QuickScan(
    val state: QuickScanUiState,
    val onClick: () -> Unit,
)

/**
 * Wires [QuickScanViewModel] to the phone's scanner: asks for availability, runs the scanner when
 * the ViewModel asks for it and reports the result back (same split as the photo picker in Import).
 */
@Composable
private fun rememberQuickScan(
    continueSession: Boolean,
    onHandoff: (sessionId: String) -> Unit,
): QuickScan {
    val viewModel: QuickScanViewModel = koinViewModel()
    val scanner = koinInject<DocumentScannerFactory>().remember()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel, scanner) {
        viewModel.onAction(QuickScanAction.AvailabilityChecked(scanner.availability()))
    }
    LaunchedEffect(viewModel, scanner) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is QuickScanEffect.OpenScanner -> {
                    viewModel.onAction(QuickScanAction.ScanFinished(scanner.scan(effect.directory)))
                }

                is QuickScanEffect.NavigateToHandoff -> {
                    onHandoff(effect.sessionId)
                }
            }
        }
    }
    return remember(state, scanner, viewModel, continueSession) {
        QuickScan(state) {
            scope.launch {
                // The scanner module may have finished downloading since the last check.
                viewModel.onAction(QuickScanAction.AvailabilityChecked(scanner.availability()))
                viewModel.onAction(QuickScanAction.Clicked(continueSession))
            }
        }
    }
}

@Composable
private fun QuickScanMessage.text(): String =
    when (this) {
        QuickScanMessage.Preparing -> stringResource(Res.string.quickscan_preparing)
        QuickScanMessage.Failed -> stringResource(Res.string.quickscan_failed)
    }

/** Home: "Quick scan" with the subtitle "Uses your phone's own scanner"; nothing when unavailable. */
@Composable
fun QuickScanHomeAction(
    onHandoff: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quickScan = rememberQuickScan(continueSession = false, onHandoff = onHandoff)
    if (!quickScan.state.visible) return
    val spacing = PlainpaperTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        PpGlassButton(
            text = stringResource(Res.string.quickscan_action),
            onClick = quickScan.onClick,
            leadingIcon = PpIcons.Document,
            enabled = !quickScan.state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(Res.string.quickscan_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = PlainpaperTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        quickScan.state.message?.let {
            Text(
                text = it.text(),
                style = MaterialTheme.typography.bodyMedium,
                color = PlainpaperTheme.colors.error,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .padding(top = spacing.xs)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Camera: a small text button above the mode switcher; it continues the camera's session. */
@Composable
fun QuickScanCameraButton(
    onHandoff: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quickScan = rememberQuickScan(continueSession = true, onHandoff = onHandoff)
    if (!quickScan.state.visible) return
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        quickScan.state.message?.let { PpHintPill(text = it.text(), tone = PpHintTone.Warning) }
        TextButton(onClick = quickScan.onClick, enabled = !quickScan.state.busy) {
            Text(
                text = stringResource(Res.string.quickscan_action),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}
