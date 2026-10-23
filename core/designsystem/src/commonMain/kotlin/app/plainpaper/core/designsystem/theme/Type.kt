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
        displaySmall =
            base.displaySmall.copy(
                fontFamily = family,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.8).sp,
            ),
        headlineMedium =
            base.headlineMedium.copy(
                fontFamily = family,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.5).sp,
            ),
        titleLarge =
            base.titleLarge.copy(
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.3).sp,
            ),
        titleMedium =
            base.titleMedium.copy(
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                lineHeight = 22.sp,
            ),
        titleSmall =
            base.titleSmall.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
        bodyLarge =
            base.bodyLarge.copy(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 23.sp,
            ),
        bodyMedium =
            base.bodyMedium.copy(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            ),
        bodySmall =
            base.bodySmall.copy(
                fontFamily = family,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
        labelLarge =
            base.labelLarge.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 22.sp,
            ),
        labelMedium =
            base.labelMedium.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
        labelSmall =
            base.labelSmall.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
    ).withDefaults(family, base)
}

private fun Typography.withDefaults(
    family: FontFamily,
    base: Typography,
): Typography =
    copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
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
