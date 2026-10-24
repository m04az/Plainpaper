package app.plainpaper.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plainpaper.core.designsystem.theme.PpColors

private val StrokeWidth = 3.dp
private val CornerRadius = 7.dp
private val CornerRing = 3.dp
private const val CORNERS = 4
private const val FLASH_ANIMATION = "captureFlash"

/**
 * The detected page outline over the preview: translucent fill, primary stroke, white corner
 * dots. [corners] are four points (TL, TR, BR, BL) in this composable's pixel coordinates; null
 * draws nothing. [flash] fades a white overlay in and out after a capture.
 */
@Composable
public fun PpOutline(
    corners: List<Offset>?,
    flash: Boolean,
    modifier: Modifier = Modifier,
) {
    val flashAlpha by animateFloatAsState(targetValue = if (flash) 1f else 0f, label = FLASH_ANIMATION)
    Canvas(modifier = modifier.semantics { invisibleToUser() }) {
        if (corners != null && corners.size == CORNERS) {
            val path =
                Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    for (i in 1 until CORNERS) lineTo(corners[i].x, corners[i].y)
                    close()
                }
            drawPath(path, PpColors.CameraOutlineFill)
            drawPath(path, PpColors.CameraOutline, style = Stroke(width = StrokeWidth.toPx()))
            corners.forEach { point ->
                drawCircle(Color.White, radius = CornerRadius.toPx(), center = point)
                drawCircle(
                    PpColors.CameraOutline,
                    radius = CornerRadius.toPx(),
                    center = point,
                    style = Stroke(width = CornerRing.toPx()),
                )
            }
        }
        if (flashAlpha > 0f) drawRect(Color.White.copy(alpha = flashAlpha))
    }
}
