package app.plainpaper.feature.capture.import

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plainpaper.core.capture.picker.PhotoPickerFactory
import app.plainpaper.core.designsystem.component.PpGlassButton
import app.plainpaper.core.designsystem.component.PpPrimaryButton
import app.plainpaper.core.designsystem.component.PpProgressTile
import app.plainpaper.core.designsystem.component.PpTileState
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.feature.capture.resources.Res
import app.plainpaper.feature.capture.resources.import_cancel
import app.plainpaper.feature.capture.resources.import_cancel_discard
import app.plainpaper.feature.capture.resources.import_cancel_keep
import app.plainpaper.feature.capture.resources.import_cancel_keep_none
import app.plainpaper.feature.capture.resources.import_cancel_title
import app.plainpaper.feature.capture.resources.import_converting_button
import app.plainpaper.feature.capture.resources.import_converting_heading
import app.plainpaper.feature.capture.resources.import_limit_body
import app.plainpaper.feature.capture.resources.import_limit_first
import app.plainpaper.feature.capture.resources.import_limit_see_pro
import app.plainpaper.feature.capture.resources.import_limit_title
import app.plainpaper.feature.capture.resources.import_privacy
import app.plainpaper.feature.capture.resources.import_progress_cd
import app.plainpaper.feature.capture.resources.import_progress_sub
import app.plainpaper.feature.capture.resources.import_ready_heading
import app.plainpaper.feature.capture.resources.import_ready_sub
import app.plainpaper.feature.capture.resources.import_review_button
import app.plainpaper.feature.capture.resources.import_skipped_sub
import app.plainpaper.feature.capture.resources.import_tile_active_cd
import app.plainpaper.feature.capture.resources.import_tile_done_cd
import app.plainpaper.feature.capture.resources.import_tile_queued_cd
import app.plainpaper.feature.capture.resources.import_tile_skipped_cd
import app.plainpaper.feature.capture.resources.import_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val COLUMNS = 4
private const val TILT_DEGREES = 5f
private val ProgressHeight = 8.dp
private val CancelWidth = 80.dp
private val PrivacyIconSize = 22.dp

@Composable
fun ImportRoute(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onHandoff: (sessionId: String) -> Unit,
    onPro: () -> Unit,
) {
    val viewModel: ImportViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = koinInject<PhotoPickerFactory>().remember()
    LaunchedEffect(viewModel, picker) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ImportEffect.OpenPicker -> viewModel.onAction(ImportAction.Picked(picker.pick(effect.maxItems)))
                ImportEffect.NavigateBack -> onBack()
                ImportEffect.NavigateHome -> onHome()
                is ImportEffect.NavigateToHandoff -> onHandoff(effect.sessionId)
                ImportEffect.NavigateToPro -> onPro()
            }
        }
    }
    ImportScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImportScreen(
    state: ImportUiState,
    onAction: (ImportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onAction(ImportAction.Cancel) }
    val spacing = PlainpaperTheme.spacing
    val colors = PlainpaperTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xl, vertical = spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PpGlassButton(text = stringResource(Res.string.import_cancel), onClick = { onAction(ImportAction.Cancel) })
            Text(
                text = stringResource(Res.string.import_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(CancelWidth))
        }
        if (state.phase == ImportPhase.Picking) {
            Spacer(modifier = Modifier.weight(1f))
            Footer(state, onAction)
            return@Column
        }
        Heading(state)
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = spacing.xxl, vertical = spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            items(state.items, key = { it.index }) { item -> Tile(item) }
        }
        Footer(state, onAction)
    }
    when (state.phase) {
        ImportPhase.LimitSheet -> LimitDialog(state, onAction)
        ImportPhase.CancelChoice -> CancelDialog(state, onAction)
        else -> Unit
    }
}

@Composable
private fun Heading(state: ImportUiState) {
    val spacing = PlainpaperTheme.spacing
    val finished = state.phase == ImportPhase.Finished
    val heading =
        if (finished) {
            pluralStringResource(Res.plurals.import_ready_heading, state.doneCount, state.doneCount)
        } else {
            pluralStringResource(Res.plurals.import_converting_heading, state.total, state.total)
        }
    val sub =
        when {
            finished && state.skippedCount > 0 -> {
                pluralStringResource(
                    Res.plurals.import_skipped_sub,
                    state.skippedCount,
                    state.skippedCount,
                )
            }

            finished -> {
                stringResource(Res.string.import_ready_sub)
            }

            else -> {
                stringResource(
                    Res.string.import_progress_sub,
                    state.finishedCount,
                    state.total,
                    state.estimatedSecondsLeft,
                )
            }
        }
    val progressDescription = stringResource(Res.string.import_progress_cd)
    Column(
        modifier = Modifier.padding(horizontal = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Text(text = heading, style = MaterialTheme.typography.headlineMedium, color = PlainpaperTheme.colors.onSurface)
        Text(text = sub, style = MaterialTheme.typography.bodyLarge, color = PlainpaperTheme.colors.onSurfaceVariant)
        val fraction = if (state.total == 0) 0f else state.finishedCount.toFloat() / state.total
        LinearProgressIndicator(
            progress = { fraction },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .semantics {
                        contentDescription = progressDescription
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(state.finishedCount.toFloat(), 0f..state.total.toFloat())
                    },
            color = PlainpaperTheme.colors.primary,
            trackColor = PlainpaperTheme.colors.outline,
        )
    }
}

@Composable
private fun Tile(item: ImportItem) {
    val (tileState, description) =
        when (item.state) {
            ImportItemState.Queued -> PpTileState.Queued to stringResource(Res.string.import_tile_queued_cd)
            ImportItemState.Converting -> PpTileState.Active to stringResource(Res.string.import_tile_active_cd)
            is ImportItemState.Done -> PpTileState.Done to stringResource(Res.string.import_tile_done_cd)
            ImportItemState.Skipped -> PpTileState.Skipped to stringResource(Res.string.import_tile_skipped_cd)
        }
    PpProgressTile(
        state = tileState,
        thumbnail = item.thumbnail,
        contentDescription = description,
        tiltDegrees = ((item.index % 3) - 1) * TILT_DEGREES,
    )
}

@Composable
private fun Footer(
    state: ImportUiState,
    onAction: (ImportAction) -> Unit,
) {
    val spacing = PlainpaperTheme.spacing
    Column(
        modifier = Modifier.padding(horizontal = spacing.xxl, vertical = spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Surface(shape = PlainpaperTheme.shapes.card, color = PlainpaperTheme.colors.surface) {
            Row(
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.m),
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    PpIcons.Lock,
                    contentDescription = null,
                    tint = PlainpaperTheme.colors.primary,
                    modifier = Modifier.size(PrivacyIconSize),
                )
                Text(
                    text = stringResource(Res.string.import_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlainpaperTheme.colors.onSurfaceVariant,
                )
            }
        }
        if (state.phase == ImportPhase.Finished) {
            PpPrimaryButton(
                text = pluralStringResource(Res.plurals.import_review_button, state.doneCount, state.doneCount),
                onClick = { onAction(ImportAction.Review) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.doneCount > 0,
            )
        } else {
            PpGlassButton(
                text = stringResource(Res.string.import_converting_button),
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LimitDialog(
    state: ImportUiState,
    onAction: (ImportAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(ImportAction.Cancel) },
        title = { Text(stringResource(Res.string.import_limit_title)) },
        text = { Text(stringResource(Res.string.import_limit_body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {
                onAction(ImportAction.ImportFirst)
            }) { Text(stringResource(Res.string.import_limit_first, state.limit)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(ImportAction.SeePro) },
            ) { Text(stringResource(Res.string.import_limit_see_pro)) }
        },
    )
}

@Composable
private fun CancelDialog(
    state: ImportUiState,
    onAction: (ImportAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.import_cancel_title)) },
        confirmButton = {
            TextButton(onClick = { onAction(ImportAction.KeepConverted) }) {
                Text(
                    if (state.doneCount > 0) {
                        pluralStringResource(Res.plurals.import_cancel_keep, state.doneCount, state.doneCount)
                    } else {
                        stringResource(Res.string.import_cancel_keep_none)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ImportAction.DiscardConverted) }) {
                Text(stringResource(Res.string.import_cancel_discard), color = PlainpaperTheme.colors.error)
            }
        },
    )
}
