package app.plainpaper.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Corner radii from the design token sheet. Buttons, chips and the tab bar are capsules. */
@Immutable
public data class PpShapes(
    val thumbnail: CornerBasedShape = RoundedCornerShape(4.dp),
    val search: CornerBasedShape = RoundedCornerShape(12.dp),
    val card: CornerBasedShape = RoundedCornerShape(20.dp),
    val toolbar: CornerBasedShape = RoundedCornerShape(30.dp),
    val sheet: CornerBasedShape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
    val capsule: CornerBasedShape = CircleShape,
)

public val LocalPpShapes: ProvidableCompositionLocal<PpShapes> = staticCompositionLocalOf { PpShapes() }

internal fun materialShapes(shapes: PpShapes): Shapes =
    Shapes(
        extraSmall = shapes.thumbnail,
        small = shapes.search,
        medium = shapes.card,
        large = shapes.toolbar,
        extraLarge = RoundedCornerShape(38.dp),
    )
