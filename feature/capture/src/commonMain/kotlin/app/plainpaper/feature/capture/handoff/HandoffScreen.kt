package app.plainpaper.feature.capture.handoff

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plainpaper.core.designsystem.component.PpPrimaryButton
import app.plainpaper.core.designsystem.component.PpToolButton
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.feature.capture.LookChipRow
import app.plainpaper.feature.capture.resources.Res
import app.plainpaper.feature.capture.resources.handoff_apply_all
import app.plainpaper.feature.capture.resources.handoff_attention_cd
import app.plainpaper.feature.capture.resources.handoff_back
import app.plainpaper.feature.capture.resources.handoff_done
import app.plainpaper.feature.capture.resources.handoff_failed_cd
import app.plainpaper.feature.capture.resources.handoff_page_cd
import app.plainpaper.feature.capture.resources.handoff_page_counter
import app.plainpaper.feature.capture.resources.handoff_processing_cd
import app.plainpaper.feature.capture.resources.review_flip
import app.plainpaper.feature.capture.resources.review_rotate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val BackButtonSize = 48.dp
private val BadgeSize = 28.dp

@Composable
fun HandoffRoute(
    sessionId: String,
    onHome: () -> Unit,
) {
    val viewModel: HandoffViewModel = koinViewModel { parametersOf(sessionId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HandoffEffect.NavigateHome -> onHome()
            }
        }
    }
    HandoffScreen(state = state, onAction = viewModel::onAction)
}

/** Pages one at a time, swipe between them, pick a look per page or for all (until Feature 4). */
@Composable
fun HandoffScreen(
    state: HandoffUiState,
    onAction: (HandoffAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PlainpaperTheme.spacing
    val pager = rememberPagerState(initialPage = state.current) { state.pages.size }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { onAction(HandoffAction.SelectPage(it)) }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(PlainpaperTheme.colors.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = spacing.s)) {
            IconButton(onClick = { onAction(HandoffAction.BackToHome) }, modifier = Modifier.size(BackButtonSize)) {
                Icon(
                    PpIcons.ArrowBack,
                    contentDescription = stringResource(Res.string.handoff_back),
                    tint = PlainpaperTheme.colors.onSurface,
                )
            }
            Text(
                text =
                    if (state.pages.isEmpty()) {
                        ""
                    } else {
                        stringResource(
                            Res.string.handoff_page_counter,
                            state.current + 1,
                            state.pages.size,
                        )
                    },
                style = MaterialTheme.typography.titleMedium,
                color = PlainpaperTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onAction(HandoffAction.BackToHome) },
            ) { Text(stringResource(Res.string.handoff_done)) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = state.pages.getOrNull(index) ?: return@HorizontalPager
            val image = if (index == state.current) state.preview ?: page.thumbnail else page.thumbnail
            PageView(page = page, image = image, modifier = Modifier.fillMaxSize().padding(spacing.l))
        }
        LookBar(state, onAction)
    }
}

@Composable
private fun PageView(
    page: HandoffPage,
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.handoff_page_cd, page.position + 1)
    Box(modifier = modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().shadow(6.dp),
            )
        }
        when (page.state) {
            PageState.Pending, PageState.Processing -> {
                val processing = stringResource(Res.string.handoff_processing_cd)
                CircularProgressIndicator(
                    modifier = Modifier.size(BadgeSize * 2).semantics { contentDescription = processing },
                    color = PlainpaperTheme.colors.primary,
                )
            }

            is PageState.NeedsAttention -> {
                Badge(stringResource(Res.string.handoff_attention_cd), Modifier.align(Alignment.TopEnd))
            }

            is PageState.Failed -> {
                Badge(stringResource(Res.string.handoff_failed_cd), Modifier.align(Alignment.TopEnd))
            }

            PageState.Ready -> {}
        }
    }
}

@Composable
private fun LookBar(
    state: HandoffUiState,
    onAction: (HandoffAction) -> Unit,
) {
    val spacing = PlainpaperTheme.spacing
    val current = state.currentPage
    Surface(color = PlainpaperTheme.colors.surface, shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.m)) {
            LookChipRow(
                selected = current?.style,
                enabled = current != null && !current.busy,
                onSelect = { onAction(HandoffAction.ChooseLook(it)) },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l),
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                PpToolButton(
                    icon = PpIcons.RotateClockwise,
                    text = stringResource(Res.string.review_rotate),
                    onClick = { onAction(HandoffAction.RotatePage) },
                    enabled = current != null && !current.busy,
                )
                PpToolButton(
                    icon = PpIcons.FlipHorizontal,
                    text = stringResource(Res.string.review_flip),
                    onClick = { onAction(HandoffAction.FlipPage) },
                    enabled = current != null && !current.busy,
                )
            }
            if (state.pages.size > 1) {
                PpPrimaryButton(
                    text = stringResource(Res.string.handoff_apply_all),
                    onClick = { onAction(HandoffAction.ApplyLookToAll) },
                    enabled = state.pages.none { it.busy },
                    modifier = Modifier.fillMaxWidth().padding(start = spacing.l, end = spacing.l, top = spacing.m),
                )
            }
        }
    }
}

@Composable
private fun Badge(
    description: String,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(PlainpaperTheme.spacing.s)
                .size(BadgeSize)
                .background(PlainpaperTheme.colors.error, PlainpaperTheme.shapes.capsule),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PpIcons.Warning,
            contentDescription = description,
            tint = PlainpaperTheme.colors.surface,
            modifier =
                Modifier.size(BadgeSize / 2),
        )
    }
}
