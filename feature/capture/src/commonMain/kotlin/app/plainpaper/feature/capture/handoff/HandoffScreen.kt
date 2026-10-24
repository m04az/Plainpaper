package app.plainpaper.feature.capture.handoff

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plainpaper.core.designsystem.component.PpPrimaryButton
import app.plainpaper.core.designsystem.component.PpTopTitle
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.feature.capture.resources.Res
import app.plainpaper.feature.capture.resources.handoff_attention_cd
import app.plainpaper.feature.capture.resources.handoff_back
import app.plainpaper.feature.capture.resources.handoff_count
import app.plainpaper.feature.capture.resources.handoff_failed_cd
import app.plainpaper.feature.capture.resources.handoff_page_cd
import app.plainpaper.feature.capture.resources.handoff_processing
import app.plainpaper.feature.capture.resources.handoff_processing_cd
import app.plainpaper.feature.capture.resources.handoff_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val COLUMNS = 3
private const val PAGE_ASPECT = 0.707f
private val BadgeSize = 22.dp
private val BackButtonSize = 48.dp

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

/** Placeholder until the review feature: the session's pages with their state (contracts/ui.md). */
@Composable
fun HandoffScreen(
    state: HandoffUiState,
    onAction: (HandoffAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PlainpaperTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    PlainpaperTheme.colors.background,
                ).statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = spacing.s)) {
            IconButton(onClick = { onAction(HandoffAction.BackToHome) }, modifier = Modifier.size(BackButtonSize)) {
                Icon(
                    PpIcons.ArrowBack,
                    contentDescription = stringResource(Res.string.handoff_back),
                    tint = PlainpaperTheme.colors.onSurface,
                )
            }
            PpTopTitle(text = stringResource(Res.string.handoff_title))
        }
        Text(
            text =
                buildString {
                    append(pluralStringResource(Res.plurals.handoff_count, state.pages.size, state.pages.size))
                    if (state.processingRemaining > 0) {
                        append(" · ")
                        append(
                            pluralStringResource(
                                Res.plurals.handoff_processing,
                                state.processingRemaining,
                                state.processingRemaining,
                            ),
                        )
                    }
                },
            style = MaterialTheme.typography.bodyMedium,
            color = PlainpaperTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.s),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(spacing.l),
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            items(state.pages, key = { it.id }) { page -> PageTile(page) }
        }
        PpPrimaryButton(
            text = stringResource(Res.string.handoff_back),
            onClick = { onAction(HandoffAction.BackToHome) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xl, vertical = spacing.l),
        )
    }
}

@Composable
private fun PageTile(page: HandoffPage) {
    val description = stringResource(Res.string.handoff_page_cd, page.position + 1)
    Box(
        modifier =
            Modifier
                .aspectRatio(PAGE_ASPECT)
                .clip(PlainpaperTheme.shapes.search)
                .background(PlainpaperTheme.extended.paper)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        page.thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        when (val state = page.state) {
            PageState.Pending, PageState.Processing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(BadgeSize).semantics { contentDescription = "" },
                    color = PlainpaperTheme.colors.primary,
                )
            }

            is PageState.NeedsAttention -> {
                Badge(PpIcons.Warning, stringResource(Res.string.handoff_attention_cd))
            }

            is PageState.Failed -> {
                Badge(PpIcons.Warning, stringResource(Res.string.handoff_failed_cd))
            }

            PageState.Ready -> {
                Unit
            }
        }
    }
}

@Composable
private fun Badge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
) {
    Box(
        modifier =
            Modifier
                .padding(PlainpaperTheme.spacing.xs)
                .size(BadgeSize)
                .background(PlainpaperTheme.colors.error, PlainpaperTheme.shapes.capsule),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = PlainpaperTheme.colors.surface,
            modifier =
                Modifier.size(BadgeSize / 2),
        )
    }
}
