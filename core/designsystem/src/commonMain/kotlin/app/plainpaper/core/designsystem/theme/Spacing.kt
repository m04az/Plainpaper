package app.plainpaper.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale 4 / 8 / 12 / 16 / 20 / 24 / 32 dp from the design token sheet. */
@Immutable
public data class Spacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
)

public val LocalSpacing: ProvidableCompositionLocal<Spacing> = staticCompositionLocalOf { Spacing() }
