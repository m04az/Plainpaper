package app.plainpaper.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plainpaper.core.designsystem.component.PpEmptyState
import app.plainpaper.core.designsystem.component.PpGlassButton
import app.plainpaper.core.designsystem.component.PpPrimaryButton
import app.plainpaper.core.designsystem.component.PpTopTitle
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.feature.home.resources.Res
import app.plainpaper.feature.home.resources.home_empty_body
import app.plainpaper.feature.home.resources.home_empty_illustration_cd
import app.plainpaper.feature.home.resources.home_empty_title
import app.plainpaper.feature.home.resources.home_import_button
import app.plainpaper.feature.home.resources.home_recovery_continue
import app.plainpaper.feature.home.resources.home_recovery_discard
import app.plainpaper.feature.home.resources.home_recovery_title
import app.plainpaper.feature.home.resources.home_scan_button
import app.plainpaper.feature.home.resources.home_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Space reserved at the bottom so content clears the floating tab bar. */
private val BottomBarClearance = 120.dp

@Composable
fun HomeRoute(
    onNavigateToScan: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToHandoff: (sessionId: String) -> Unit,
    quickScanAction: @Composable (Modifier) -> Unit = {},
) {
    val viewModel: HomeViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event ==
                    Lifecycle.Event.ON_START
                ) {
                    viewModel.onAction(HomeAction.ScreenStarted)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HomeEffect.NavigateToScan -> onNavigateToScan()
                HomeEffect.NavigateToImport -> onNavigateToImport()
                is HomeEffect.NavigateToHandoff -> onNavigateToHandoff(effect.sessionId)
            }
        }
    }

    HomeScreen(state = state, onAction = viewModel::onAction, quickScanAction = quickScanAction)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    quickScanAction: @Composable (Modifier) -> Unit = {},
) {
    val spacing = PlainpaperTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(top = spacing.xl, bottom = BottomBarClearance),
    ) {
        PpTopTitle(text = stringResource(Res.string.home_title))
        state.recoverable?.let { RecoveryCard(it, onAction) }
        if (state.isEmpty) {
            PpEmptyState(
                title = stringResource(Res.string.home_empty_title),
                body = stringResource(Res.string.home_empty_body),
                illustration = { EmptyIllustration() },
                primaryAction = {
                    PpPrimaryButton(
                        text = stringResource(Res.string.home_scan_button),
                        onClick = { onAction(HomeAction.ScanClicked) },
                        leadingIcon = PpIcons.ScanFrame,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                secondaryAction = {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                        PpGlassButton(
                            text = stringResource(Res.string.home_import_button),
                            onClick = { onAction(HomeAction.ImportClicked) },
                            leadingIcon = PpIcons.ImportPhoto,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        quickScanAction(Modifier.fillMaxWidth())
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = spacing.xxxl * 2),
            )
        }
    }
}

/** "You have an unfinished scan of N pages" with Continue and Discard (spec 003 FR-011). */
@Composable
private fun RecoveryCard(
    recoverable: RecoverableSession,
    onAction: (HomeAction) -> Unit,
) {
    val spacing = PlainpaperTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l, vertical = spacing.m),
        shape = PlainpaperTheme.shapes.card,
        color = PlainpaperTheme.colors.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(spacing.l)) {
            Text(
                text =
                    pluralStringResource(
                        Res.plurals.home_recovery_title,
                        recoverable.pageCount,
                        recoverable.pageCount,
                    ),
                style = MaterialTheme.typography.titleMedium,
                color = PlainpaperTheme.colors.onPrimaryContainer,
            )
            Column(
                modifier = Modifier.padding(top = spacing.m),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                PpPrimaryButton(
                    text = stringResource(Res.string.home_recovery_continue),
                    onClick = { onAction(HomeAction.ContinueRecovery) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PpGlassButton(
                    text = stringResource(Res.string.home_recovery_discard),
                    onClick = { onAction(HomeAction.DiscardRecovery) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val IllustrationWidth = 150.dp
private val IllustrationHeight = 170.dp
private val PageWidth = 110.dp
private val PageHeight = 146.dp
private val BackPageRotation = -7f
private val LineHeight = 6.dp

/** Two stacked sheets, the front one with faint text lines (design "Home — empty"). */
@Composable
private fun EmptyIllustration() {
    val description = stringResource(Res.string.home_empty_illustration_cd)
    val paper = PlainpaperTheme.extended.paper
    val lineColor = PlainpaperTheme.colors.outline
    Box(
        modifier =
            Modifier
                .size(IllustrationWidth, IllustrationHeight)
                .semantics { contentDescription = description },
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp, top = 18.dp)
                    .size(PageWidth, PageHeight)
                    .rotate(BackPageRotation)
                    .background(lineColor, PlainpaperTheme.shapes.thumbnail),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 10.dp, top = 6.dp)
                    .size(PageWidth, PageHeight)
                    .shadow(16.dp, PlainpaperTheme.shapes.thumbnail)
                    .background(paper, PlainpaperTheme.shapes.thumbnail)
                    .padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            listOf(0.7f, 0.9f, 0.8f, 0.5f).forEach { fraction ->
                Box(
                    modifier =
                        Modifier
                            .width(PageWidth * fraction)
                            .height(LineHeight)
                            .background(lineColor, PlainpaperTheme.shapes.thumbnail),
                )
            }
        }
    }
}
