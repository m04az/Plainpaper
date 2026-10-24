package app.plainpaper.feature.capture.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.plainpaper.core.designsystem.component.PpCameraPrimaryPill
import app.plainpaper.core.designsystem.component.PpPageCounter
import app.plainpaper.core.designsystem.component.PpPrimaryButton
import app.plainpaper.core.designsystem.component.PpToolButton
import app.plainpaper.core.designsystem.icon.PpIcons
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.designsystem.theme.PpColors
import app.plainpaper.feature.capture.LookChipRow
import app.plainpaper.feature.capture.resources.Res
import app.plainpaper.feature.capture.resources.camera_done
import app.plainpaper.feature.capture.resources.camera_pages_cd
import app.plainpaper.feature.capture.resources.review_failed
import app.plainpaper.feature.capture.resources.review_flip
import app.plainpaper.feature.capture.resources.review_preview_cd
import app.plainpaper.feature.capture.resources.review_processing
import app.plainpaper.feature.capture.resources.review_retake
import app.plainpaper.feature.capture.resources.review_rotate
import app.plainpaper.feature.capture.resources.review_save
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val GROW_MILLIS = 350
private const val SCRIM_ALPHA = 0.94f
private const val START_ALPHA = 0.5f
private const val DIMMED_ALPHA = 0.45f
private const val PLACEHOLDER_FRACTION = 0.4f
private val PagePadding = 16.dp
private val PlaceholderCorner = 8.dp
private val SpinnerSize = 40.dp
private val OutlineStroke = 2.dp

/**
 * Review of the page just shot (spec 003 US7): the page grows from its outline into a large
 * preview with the four looks, Rotate, Flip, Retake and Save. It covers the live camera.
 */
@Composable
internal fun ReviewOverlay(
    state: CameraUiState,
    review: ReviewState,
    onAction: (CameraAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .onGloballyPositioned { origin = it.positionInRoot() }
                // The overlay swallows touches so nothing reaches the camera behind it.
                .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ReviewTopBar(state, onAction)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { area = it.boundsInRoot().translate(-origin) },
            )
            ReviewControls(review, onAction)
        }
        ReviewPage(review, area)
    }
}

@Composable
private fun ReviewTopBar(
    state: CameraUiState,
    onAction: (CameraAction) -> Unit,
) {
    val spacing = PlainpaperTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l, vertical = spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PpPageCounter(
            count = state.pageCount,
            thumbnail = state.latestThumbnail,
            contentDescription = pluralStringResource(Res.plurals.camera_pages_cd, state.pageCount, state.pageCount),
        )
        PpCameraPrimaryPill(text = stringResource(Res.string.camera_done), onClick = { onAction(CameraAction.Done) })
    }
}

@Composable
private fun ReviewControls(
    review: ReviewState,
    onAction: (CameraAction) -> Unit,
) {
    val spacing = PlainpaperTheme.spacing
    Surface(color = PlainpaperTheme.colors.surface, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.m, bottom = spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            LookChipRow(
                selected = review.style,
                enabled = !review.processing,
                onSelect = { onAction(CameraAction.ReviewLook(it)) },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PpToolButton(
                    icon = PpIcons.RotateClockwise,
                    text = stringResource(Res.string.review_rotate),
                    onClick = { onAction(CameraAction.ReviewRotate) },
                    enabled = !review.processing && !review.failed,
                )
                PpToolButton(
                    icon = PpIcons.FlipHorizontal,
                    text = stringResource(Res.string.review_flip),
                    onClick = { onAction(CameraAction.ReviewFlip) },
                    enabled = !review.processing && !review.failed,
                )
                PpToolButton(
                    icon = PpIcons.ScanFrame,
                    text = stringResource(Res.string.review_retake),
                    onClick = { onAction(CameraAction.ReviewRetake) },
                )
            }
            PpPrimaryButton(
                text = stringResource(Res.string.review_save),
                onClick = { onAction(CameraAction.ReviewSave) },
                enabled = !review.failed,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l),
            )
        }
    }
}

/**
 * The page image. Until it arrives a spinner sits inside the outline box; then the image
 * grows from that box to a fitted box centred in [area] (scale + translation + fade).
 */
@Composable
private fun ReviewPage(
    review: ReviewState,
    area: Rect,
) {
    if (area.isEmpty) return
    val density = LocalDensity.current
    val preview = review.preview
    val start = review.startOutline?.let(::boundsOf) ?: placeholderBounds(area)
    if (preview == null) {
        PlaceholderBox(start, review)
        return
    }
    val target = fitted(preview.width, preview.height, area)
    val progress = remember(review.pageId) { Animatable(0f) }
    LaunchedEffect(review.pageId) { progress.animateTo(1f, tween(GROW_MILLIS)) }
    val description = stringResource(Res.string.review_preview_cd)
    val dim = if (review.processing) DIMMED_ALPHA else 1f
    Box(
        modifier =
            Modifier
                .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
                .size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
                .graphicsLayer {
                    val p = progress.value
                    translationX = lerp(start.center.x - target.center.x, 0f, p)
                    translationY = lerp(start.center.y - target.center.y, 0f, p)
                    scaleX = lerp(start.width / target.width, 1f, p)
                    scaleY = lerp(start.height / target.height, 1f, p)
                    alpha = lerp(START_ALPHA, 1f, p) * dim
                }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = preview,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (review.processing || review.failed) {
        StatusBox(target, review)
    }
}

/** Outline-shaped placeholder shown while the first render is still running. */
@Composable
private fun PlaceholderBox(
    bounds: Rect,
    review: ReviewState,
) {
    val density = LocalDensity.current
    Box(
        modifier =
            Modifier
                .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .border(OutlineStroke, PpColors.CameraOutline, RoundedCornerShape(PlaceholderCorner)),
        contentAlignment = Alignment.Center,
    ) {
        StatusContent(review)
    }
}

/** Spinner or failure text centred on the page. */
@Composable
private fun StatusBox(
    bounds: Rect,
    review: ReviewState,
) {
    val density = LocalDensity.current
    Box(
        modifier =
            Modifier
                .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() }),
        contentAlignment = Alignment.Center,
    ) {
        StatusContent(review)
    }
}

@Composable
private fun StatusContent(review: ReviewState) {
    if (review.failed) {
        Text(
            text = stringResource(Res.string.review_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = PpColors.CameraOnGlass,
            modifier = Modifier.padding(PlainpaperTheme.spacing.l),
        )
    } else {
        val description = stringResource(Res.string.review_processing)
        CircularProgressIndicator(
            modifier = Modifier.size(SpinnerSize).semantics { contentDescription = description },
            color = PpColors.CameraOnGlass,
        )
    }
}

private fun boundsOf(points: List<Offset>): Rect {
    val left = points.minOf { it.x }
    val top = points.minOf { it.y }
    val right = points.maxOf { it.x }
    val bottom = points.maxOf { it.y }
    return Rect(left, top, maxOf(right, left + 1f), maxOf(bottom, top + 1f))
}

private fun placeholderBounds(area: Rect): Rect {
    val size = Size(area.width * PLACEHOLDER_FRACTION, area.height * PLACEHOLDER_FRACTION)
    return Rect(area.center - Offset(size.width / 2f, size.height / 2f), size)
}

/** The largest box of the image's aspect that fits [area] minus the page padding, centred. */
@Composable
private fun fitted(
    imageWidth: Int,
    imageHeight: Int,
    area: Rect,
): Rect {
    val padding = with(LocalDensity.current) { PagePadding.toPx() }
    val availableWidth = (area.width - 2 * padding).coerceAtLeast(1f)
    val availableHeight = (area.height - 2 * padding).coerceAtLeast(1f)
    val scale = minOf(availableWidth / imageWidth, availableHeight / imageHeight)
    val size = Size(imageWidth * scale, imageHeight * scale)
    return Rect(area.center - Offset(size.width / 2f, size.height / 2f), size)
}
