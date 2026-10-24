package app.plainpaper.feature.capture.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plainpaper.core.capture.camera.CameraPreviewRenderer
import app.plainpaper.core.capture.permission.CameraPermissionFactory
import app.plainpaper.core.designsystem.component.PpCameraPrimaryPill
import app.plainpaper.core.designsystem.component.PpGlassIconButton
import app.plainpaper.core.designsystem.component.PpGlassPill
import app.plainpaper.core.designsystem.component.PpHintPill
import app.plainpaper.core.designsystem.component.PpHintTone
import app.plainpaper.core.designsystem.component.PpOutline
import app.plainpaper.core.designsystem.component.PpPageCounter
import app.plainpaper.core.designsystem.component.PpSegment
import app.plainpaper.core.designsystem.component.PpSegmentedControl
import app.plainpaper.core.designsystem.component.PpShutterButton
import app.plainpaper.core.designsystem.component.PpToggleChip
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.designsystem.theme.PpColors
import app.plainpaper.core.domain.model.capture.CameraPermissionState
import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.feature.capture.permission.PermissionDeniedPanel
import app.plainpaper.feature.capture.permission.PermissionExplanationPanel
import app.plainpaper.feature.capture.resources.Res
import app.plainpaper.feature.capture.resources.camera_auto
import app.plainpaper.feature.capture.resources.camera_auto_cd
import app.plainpaper.feature.capture.resources.camera_capture_failed
import app.plainpaper.feature.capture.resources.camera_close_cd
import app.plainpaper.feature.capture.resources.camera_discard_body
import app.plainpaper.feature.capture.resources.camera_discard_confirm
import app.plainpaper.feature.capture.resources.camera_discard_keep
import app.plainpaper.feature.capture.resources.camera_discard_title
import app.plainpaper.feature.capture.resources.camera_done
import app.plainpaper.feature.capture.resources.camera_flash_cd
import app.plainpaper.feature.capture.resources.camera_flash_off
import app.plainpaper.feature.capture.resources.camera_flash_on
import app.plainpaper.feature.capture.resources.camera_hint_adjust
import app.plainpaper.feature.capture.resources.camera_hint_hold_steady
import app.plainpaper.feature.capture.resources.camera_hint_id_back
import app.plainpaper.feature.capture.resources.camera_hint_id_front
import app.plainpaper.feature.capture.resources.camera_hint_move_closer
import app.plainpaper.feature.capture.resources.camera_hint_receipt
import app.plainpaper.feature.capture.resources.camera_hint_tap_shutter
import app.plainpaper.feature.capture.resources.camera_hint_too_dark
import app.plainpaper.feature.capture.resources.camera_mode_document
import app.plainpaper.feature.capture.resources.camera_mode_id_card
import app.plainpaper.feature.capture.resources.camera_mode_receipt
import app.plainpaper.feature.capture.resources.camera_modes_cd
import app.plainpaper.feature.capture.resources.camera_pages_cd
import app.plainpaper.feature.capture.resources.camera_shutter_cd
import app.plainpaper.feature.capture.resources.camera_storage_full
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val TopBarHeight = 44.dp
private val HintTop = 64.dp
private val SideSlot = 64.dp
private val BottomPadding = 34.dp

@Composable
fun CameraRoute(
    onBack: () -> Unit,
    onHandoff: (sessionId: String) -> Unit,
    onImport: (noCamera: Boolean) -> Unit,
) {
    val viewModel: CameraViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = koinInject<CameraPermissionFactory>().remember()
    val renderer: CameraPreviewRenderer = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, permission) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        viewModel.onAction(CameraAction.PermissionStatus(permission.status()))
                        viewModel.onAction(CameraAction.ScreenStarted)
                    }

                    Lifecycle.Event.ON_STOP -> {
                        viewModel.onAction(CameraAction.ScreenStopped)
                    }

                    else -> {
                        Unit
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel, permission) {
        viewModel.effects.collect { effect ->
            when (effect) {
                CameraEffect.RequestPermission -> {
                    viewModel.onAction(
                        CameraAction.PermissionStatus(permission.request()),
                    )
                }

                CameraEffect.OpenSettings -> {
                    permission.openSettings()
                }

                is CameraEffect.NavigateToImport -> {
                    onImport(effect.noCamera)
                }

                is CameraEffect.NavigateToHandoff -> {
                    onHandoff(effect.sessionId)
                }

                CameraEffect.NavigateBack -> {
                    onBack()
                }
            }
        }
    }

    CameraScreen(
        state = state,
        onAction = viewModel::onAction,
        preview = { modifier -> renderer.Render(viewModel.camera, modifier) },
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CameraScreen(
    state: CameraUiState,
    onAction: (CameraAction) -> Unit,
    preview: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onAction(CameraAction.Close) }
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when (state.permission) {
            CameraPermissionState.Granted -> {
                preview(
                    Modifier.fillMaxSize().onSizeChanged {
                        onAction(
                            CameraAction.PreviewSizeChanged(it.width, it.height),
                        )
                    },
                )
                PpOutline(corners = state.outline, flash = state.capturing, modifier = Modifier.fillMaxSize())
                CameraChrome(state, onAction)
            }

            CameraPermissionState.NotAsked -> {
                PermissionExplanationPanel(
                    onAllow = { onAction(CameraAction.AllowCamera) },
                    onImportInstead = { onAction(CameraAction.ImportInstead) },
                )
            }

            else -> {
                PermissionDeniedPanel(
                    canPromptAgain = state.permission == CameraPermissionState.Denied,
                    onRetry = { onAction(CameraAction.AllowCamera) },
                    onOpenSettings = { onAction(CameraAction.OpenSettings) },
                    onImportInstead = { onAction(CameraAction.ImportInstead) },
                )
            }
        }
        if (state.permission != CameraPermissionState.Granted) {
            Box(modifier = Modifier.statusBarsPadding().padding(PlainpaperTheme.spacing.l)) {
                PpGlassIconButton(
                    icon = PpIcons.Close,
                    contentDescription = stringResource(Res.string.camera_close_cd),
                    onClick = { onAction(CameraAction.Close) },
                )
            }
        }
    }
    if (state.showDiscardConfirm) DiscardDialog(state.pageCount, onAction)
}

@Composable
private fun CameraChrome(
    state: CameraUiState,
    onAction: (CameraAction) -> Unit,
) {
    val spacing = PlainpaperTheme.spacing
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l, vertical = spacing.s),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PpGlassIconButton(
                icon = PpIcons.Close,
                contentDescription = stringResource(Res.string.camera_close_cd),
                onClick = { onAction(CameraAction.Close) },
            )
            PpGlassPill {
                PpToggleChip(
                    text =
                        if (state.flash) {
                            stringResource(
                                Res.string.camera_flash_on,
                            )
                        } else {
                            stringResource(Res.string.camera_flash_off)
                        },
                    selected = state.flash,
                    onToggle = { onAction(CameraAction.SetFlash(it)) },
                    contentDescription = stringResource(Res.string.camera_flash_cd),
                    leadingIcon = PpIcons.Flash,
                    iconTint = if (state.flash) PpColors.CameraWarning else null,
                )
                PpToggleChip(
                    text = stringResource(Res.string.camera_auto),
                    selected = state.autoCapture,
                    onToggle = { onAction(CameraAction.SetAutoCapture(it)) },
                    contentDescription = stringResource(Res.string.camera_auto_cd),
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = HintTop - TopBarHeight),
            contentAlignment = Alignment.Center,
        ) {
            PpHintPill(text = hintText(state), tone = hintTone(state))
        }
        Spacer(modifier = Modifier.weight(1f))
        Banner(state, onAction)
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = spacing.xl, end = spacing.xl, bottom = BottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            PpSegmentedControl(
                segments =
                    listOf(
                        PpSegment(CaptureMode.Document.name, stringResource(Res.string.camera_mode_document)),
                        PpSegment(CaptureMode.IdCard.name, stringResource(Res.string.camera_mode_id_card)),
                        PpSegment(CaptureMode.Receipt.name, stringResource(Res.string.camera_mode_receipt)),
                    ),
                selectedId = state.mode.name,
                onSelect = { onAction(CameraAction.SelectMode(CaptureMode.valueOf(it.id))) },
                contentDescription = stringResource(Res.string.camera_modes_cd),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(SideSlot), contentAlignment = Alignment.CenterStart) {
                    if (state.pageCount > 0) {
                        PpPageCounter(
                            count = state.pageCount,
                            thumbnail = state.latestThumbnail,
                            contentDescription =
                                pluralStringResource(
                                    Res.plurals.camera_pages_cd,
                                    state.pageCount,
                                    state.pageCount,
                                ),
                        )
                    }
                }
                PpShutterButton(
                    onClick = { onAction(CameraAction.Shutter) },
                    contentDescription = stringResource(Res.string.camera_shutter_cd),
                    enabled = state.isRunning && !state.storageFull,
                )
                Box(modifier = Modifier.width(SideSlot), contentAlignment = Alignment.CenterEnd) {
                    if (state.pageCount > 0) {
                        PpCameraPrimaryPill(
                            text = stringResource(Res.string.camera_done),
                            onClick = { onAction(CameraAction.Done) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(
    state: CameraUiState,
    onAction: (CameraAction) -> Unit,
) {
    val message =
        when {
            state.storageFull -> stringResource(Res.string.camera_storage_full)
            state.captureFailed -> stringResource(Res.string.camera_capture_failed)
            else -> return
        }
    Box(modifier = Modifier.fillMaxWidth().padding(PlainpaperTheme.spacing.l), contentAlignment = Alignment.Center) {
        PpHintPill(text = message, tone = PpHintTone.Warning)
    }
    if (state.captureFailed) {
        LaunchedEffect(state.captureFailed) {
            kotlinx.coroutines.delay(FAILED_BANNER_MILLIS)
            onAction(CameraAction.DismissCaptureFailed)
        }
    }
}

private const val FAILED_BANNER_MILLIS = 2_500L

@Composable
private fun DiscardDialog(
    pageCount: Int,
    onAction: (CameraAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(CameraAction.KeepScanning) },
        title = { Text(pluralStringResource(Res.plurals.camera_discard_title, pageCount, pageCount)) },
        text = { Text(stringResource(Res.string.camera_discard_body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = { onAction(CameraAction.ConfirmDiscard) }) {
                Text(stringResource(Res.string.camera_discard_confirm), color = PlainpaperTheme.colors.error)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(CameraAction.KeepScanning) },
            ) { Text(stringResource(Res.string.camera_discard_keep)) }
        },
    )
}

@Composable
private fun hintText(state: CameraUiState): String =
    when (state.hint) {
        HintKind.HoldSteady -> stringResource(Res.string.camera_hint_hold_steady)
        HintKind.TapShutter -> stringResource(Res.string.camera_hint_tap_shutter)
        HintKind.MoveCloser -> stringResource(Res.string.camera_hint_move_closer)
        HintKind.TooDark -> stringResource(Res.string.camera_hint_too_dark)
        HintKind.AdjustFraming -> stringResource(Res.string.camera_hint_adjust)
        HintKind.FrontOfCard -> stringResource(Res.string.camera_hint_id_front)
        HintKind.BackOfCard -> stringResource(Res.string.camera_hint_id_back)
        HintKind.FitReceipt -> stringResource(Res.string.camera_hint_receipt)
    }

private fun hintTone(state: CameraUiState): PpHintTone =
    when (state.gate) {
        is GateState.Tracking, GateState.Capture, GateState.Cooldown -> PpHintTone.Ready
        GateState.TooDark, GateState.LowConfidence -> PpHintTone.Warning
        is GateState.NoPage -> PpHintTone.Neutral
    }
