package app.plainpaper.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.plainpaper.core.designsystem.resources.Res
import app.plainpaper.core.designsystem.resources.ibm_plex_sans_arabic_bold
import app.plainpaper.core.designsystem.resources.ibm_plex_sans_arabic_medium
import app.plainpaper.core.designsystem.resources.ibm_plex_sans_arabic_regular
import app.plainpaper.core.designsystem.resources.ibm_plex_sans_arabic_semibold
import app.plainpaper.core.designsystem.resources.inter_bold
import app.plainpaper.core.designsystem.resources.inter_extrabold
import app.plainpaper.core.designsystem.resources.inter_medium
import app.plainpaper.core.designsystem.resources.inter_regular
import app.plainpaper.core.designsystem.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/** Inter for Latin UI. */
@Composable
public fun interFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
        Font(Res.font.inter_extrabold, FontWeight.ExtraBold),
    )

/** IBM Plex Sans Arabic for Arabic UI; it also carries Latin glyphs for mixed text. */
@Composable
public fun plexArabicFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
        Font(Res.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
        Font(Res.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
        // Plex Arabic has no 800 cut; Bold is the closest.
        Font(Res.font.ibm_plex_sans_arabic_bold, FontWeight.ExtraBold),
    )

/**
 * Type scale from the design: large title 34/800, title 28/800, title2 22/700,
 * headline 17/700, body 16/400, caption 13/500. Sizes are in sp so system text scaling applies.
 */
internal fun plainpaperTypography(family: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = style(family, FontWeight.ExtraBold, size = 34, lineHeight = 40, letterSpacing = -0.8),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = style(family, FontWeight.ExtraBold, size = 28, lineHeight = 34, letterSpacing = -0.5),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = style(family, FontWeight.Bold, size = 22, lineHeight = 28, letterSpacing = -0.3),
        titleMedium = style(family, FontWeight.Bold, size = 17, lineHeight = 22),
        titleSmall = style(family, FontWeight.SemiBold, size = 15, lineHeight = 20),
        bodyLarge = style(family, FontWeight.Normal, size = 16, lineHeight = 23),
        bodyMedium = style(family, FontWeight.Normal, size = 15, lineHeight = 21),
        bodySmall = style(family, FontWeight.Medium, size = 13, lineHeight = 18),
        labelLarge = style(family, FontWeight.SemiBold, size = 17, lineHeight = 22),
        labelMedium = style(family, FontWeight.SemiBold, size = 14, lineHeight = 18),
        labelSmall = style(family, FontWeight.SemiBold, size = 11, lineHeight = 14),
    )
}

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
): TextStyle =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
    )

/** Convenience aliases matching the design's names. */
public object PpTextStyles {
    public val Typography.largeTitle: TextStyle get() = displaySmall
    public val Typography.title: TextStyle get() = headlineMedium
    public val Typography.title2: TextStyle get() = titleLarge
    public val Typography.headline: TextStyle get() = titleMedium
    public val Typography.body: TextStyle get() = bodyLarge
    public val Typography.caption: TextStyle get() = bodySmall
}
