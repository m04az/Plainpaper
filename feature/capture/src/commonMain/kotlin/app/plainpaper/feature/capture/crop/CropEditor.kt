package app.plainpaper.feature.capture.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.component.PpPrimaryButton
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.designsystem.theme.PpColors
import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad
import app.plainpaper.feature.capture.resources.Res
import app.plainpaper.feature.capture.resources.crop_apply
import app.plainpaper.feature.capture.resources.crop_auto
import app.plainpaper.feature.capture.resources.crop_cancel
import app.plainpaper.feature.capture.resources.crop_canvas_cd
import app.plainpaper.feature.capture.resources.crop_failed
import app.plainpaper.feature.capture.resources.crop_full_photo
import app.plainpaper.feature.capture.resources.crop_loading_cd
import app.plainpaper.feature.capture.resources.crop_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val HandleRadius = 12.dp
private val HandleHitRadius = 24.dp
private val BorderStroke = 2.dp
private val MagnifierSize = 110.dp
private val MagnifierMargin = 12.dp
private val MagnifierStroke = 2.dp
private val CrosshairArm = 8.dp
private const val SCRIM_ALPHA = 0.55f
private const val MAGNIFICATION = 2f

/**
 * Full-screen editor for the page border (spec 004 FR-014): the photo, a darkened outside, the
 * border with four draggable handles (48dp touch targets) and a magnifier while a handle is held.
 * The geometry lives in [CornerEditor]; this composable only draws and feeds it gestures. The
 * handles are positional, so they do not mirror in RTL; the bottom bar does.
 */
@Composable
internal fun CropEditor(
    state: CropState,
    onApply: (Quad) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editor by remember(state.photo) { mutableStateOf<CornerEditor?>(null) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(PlainpaperTheme.colors.background)
                .statusBarsPadding(),
    ) {
        Text(
            text = stringResource(Res.string.crop_title),
            style = MaterialTheme.typography.titleMedium,
            color = PlainpaperTheme.colors.onSurface,
            modifier = Modifier.fillMaxWidth().padding(PlainpaperTheme.spacing.l),
        )
        CropCanvas(
            state = state,
            editor = editor,
            onEditor = { editor = it },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        CropBar(
            canApply = editor != null && state.photo != null,
            canAdjust = editor != null,
            onAuto = { editor = editor?.auto() },
            onFullPhoto = { editor = editor?.fullPhoto() },
            onApply = { editor?.let { onApply(it.result()) } },
            onCancel = onCancel,
        )
    }
}

private val HandleInset = 24.dp

@Composable
private fun CropCanvas(
    state: CropState,
    editor: CornerEditor?,
    onEditor: (CornerEditor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photo = state.photo
    val corners = state.corners
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var active by remember { mutableStateOf<Int?>(null) }
    val current by rememberUpdatedState(editor)
    val hitRadius = with(LocalDensity.current) { HandleHitRadius.toPx() }
    // (Re)build the editor when the photo has loaded and whenever the view is measured again.
    LaunchedEffect(photo, corners, viewSize) {
        if (photo != null && corners != null && viewSize.width > 0) {
            onEditor(
                CornerEditor(
                    imageWidth = photo.decodedWidth,
                    imageHeight = photo.decodedHeight,
                    viewWidth = viewSize.width.toFloat(),
                    viewHeight = viewSize.height.toFloat(),
                    quad = current?.quad ?: corners,
                    detected = state.detected,
                ),
            )
        }
    }
    val description = stringResource(Res.string.crop_canvas_cd)
    Box(
        modifier =
            modifier
                .background(Color.Black)
                // Inset so handles on the photo's edges stay whole and reachable.
                .padding(HandleInset)
                .onSizeChanged { viewSize = it }
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val index =
                            current?.cornerAt(Point(down.position.x, down.position.y), hitRadius)
                                ?: return@awaitEachGesture
                        down.consume()
                        active = index
                        drag(down.id) { change ->
                            val delta = change.positionChange()
                            current?.let { onEditor(it.drag(index, Point(delta.x, delta.y))) }
                            change.consume()
                        }
                        active = null
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (photo == null || editor == null) {
            if (state.failed) {
                Text(
                    text = stringResource(Res.string.crop_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PpColors.CameraOnGlass,
                    modifier = Modifier.padding(PlainpaperTheme.spacing.l),
                )
            } else {
                val loading = stringResource(Res.string.crop_loading_cd)
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp).semantics { contentDescription = loading },
                    color = PpColors.CameraOnGlass,
                )
            }
        } else {
            EditorLayers(photo.bitmap, editor, active)
        }
    }
}

@Composable
private fun EditorLayers(
    bitmap: ImageBitmap,
    editor: CornerEditor,
    active: Int?,
) {
    val density = LocalDensity.current
    val handleRadius = with(density) { HandleRadius.toPx() }
    val borderStroke = with(density) { BorderStroke.toPx() }
    val handleColor = PlainpaperTheme.colors.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        val fit = editor.fit
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
            dstSize = IntSize(fit.width.roundToInt(), fit.height.roundToInt()),
        )
        val corners = editor.viewCorners().map { Offset(it.x, it.y) }
        val border = polygon(corners)
        // Darken everything outside the border: the view rectangle minus the polygon.
        val outside =
            Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addPath(border)
            }
        drawPath(outside, Color.Black.copy(alpha = SCRIM_ALPHA))
        drawPath(border, PpColors.CameraOutline, style = Stroke(width = borderStroke, join = StrokeJoin.Round))
        corners.forEachIndexed { index, corner ->
            val radius = if (index == active) handleRadius * 1.25f else handleRadius
            drawCircle(Color.White, radius, corner)
            drawCircle(handleColor, radius, corner, style = Stroke(width = borderStroke))
        }
    }
    if (active != null) {
        Box(modifier = Modifier.fillMaxSize()) { Magnifier(bitmap, editor, active) }
    }
}

/** A circular inset at 2x with a crosshair, in the top corner away from the held handle. */
@Composable
private fun Magnifier(
    bitmap: ImageBitmap,
    editor: CornerEditor,
    active: Int,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { MagnifierSize.toPx() }
    val marginPx = with(density) { MagnifierMargin.toPx() }
    val stroke = with(density) { MagnifierStroke.toPx() }
    val arm = with(density) { CrosshairArm.toPx() }
    val corner = editor.viewCorners()[active]
    // Left or right half decides the side (absolute, not start/end: the photo does not mirror).
    val onLeft = corner.x > editor.viewWidth / 2f
    val left = if (onLeft) marginPx else editor.viewWidth - sizePx - marginPx
    val offset = IntOffset(left.roundToInt(), marginPx.roundToInt())
    val fit = editor.fit
    Canvas(modifier = Modifier.offset { offset }.size(MagnifierSize)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val circle = Path().apply { addOval(Rect(center, size.width / 2f)) }
        clipPath(circle) {
            drawRect(Color.Black)
            // The photo scaled by MAGNIFICATION around the held corner, which sits at the centre.
            val scaled = fit.scale * MAGNIFICATION
            val dst =
                Offset(
                    center.x - (corner.x - fit.left) * MAGNIFICATION,
                    center.y - (corner.y - fit.top) * MAGNIFICATION,
                )
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(dst.x.roundToInt(), dst.y.roundToInt()),
                dstSize =
                    IntSize(
                        (editor.imageWidth * scaled).roundToInt(),
                        (editor.imageHeight * scaled).roundToInt(),
                    ),
            )
        }
        drawCircle(Color.White, size.width / 2f, center, style = Stroke(width = stroke))
        crosshair(center, arm, stroke)
    }
}

private fun DrawScope.crosshair(
    center: Offset,
    arm: Float,
    stroke: Float,
) {
    drawLine(PpColors.CameraOutline, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), stroke)
    drawLine(PpColors.CameraOutline, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), stroke)
}

private fun polygon(points: List<Offset>): Path =
    Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        close()
    }

@Composable
private fun CropBar(
    canApply: Boolean,
    canAdjust: Boolean,
    onAuto: () -> Unit,
    onFullPhoto: () -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = PlainpaperTheme.spacing
    Surface(color = PlainpaperTheme.colors.surface, shadowElevation = 8.dp) {
        Column(
            modifier =
                Modifier.fillMaxWidth().navigationBarsPadding().padding(
                    vertical = spacing.m,
                    horizontal = spacing.l,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s, Alignment.CenterHorizontally),
            ) {
                OutlinedButton(onClick = onAuto, enabled = canAdjust) { Text(stringResource(Res.string.crop_auto)) }
                OutlinedButton(onClick = onFullPhoto, enabled = canAdjust) {
                    Text(stringResource(Res.string.crop_full_photo))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.crop_cancel)) }
                PpPrimaryButton(
                    text = stringResource(Res.string.crop_apply),
                    onClick = onApply,
                    enabled = canApply,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
