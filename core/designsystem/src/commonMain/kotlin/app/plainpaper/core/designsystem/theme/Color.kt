package app.plainpaper.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Plainpaper color tokens from the approved design ("Design tokens & components" board).
 * Paper and ink with one accent; document pages always stay white, even in dark mode.
 *
 * WCAG contrast (verified 2026-10-23, tasks T068): ink on background 16.6 (light) / 17.0 (dark);
 * onSurfaceVariant on surface 6.5 / 6.6; onPrimary on primary 8.0 / 8.1; primary on background
 * 7.1 / 8.1; onPrimaryContainer on primaryContainer 9.4 / 10.2; surface on success 5.2 / 7.6;
 * surface on error 6.6 / 6.0. Every text pair clears 4.5:1; `outline` is decorative only.
 */
public object PpColors {
    // Light
    public val LightBackground: Color = Color(0xFFF2F1EE)
    public val LightSurface: Color = Color(0xFFFFFFFF)
    public val LightInk: Color = Color(0xFF111214)
    public val LightInkVariant: Color = Color(0xFF5E5E63)
    public val LightPrimary: Color = Color(0xFF2340C8)
    public val LightOnPrimary: Color = Color(0xFFFFFFFF)
    public val LightPrimaryContainer: Color = Color(0xFFE8ECFB)
    public val LightOnPrimaryContainer: Color = Color(0xFF1A2F96)
    public val LightOutline: Color = Color(0xFFE2E0DA)
    public val LightSuccess: Color = Color(0xFF1C7C4A)
    public val LightError: Color = Color(0xFFB42318)
    public val LightGlassSurface: Color = Color(0x9EFFFFFF)
    public val LightGlassStroke: Color = Color(0xCCFFFFFF)

    // Dark
    public val DarkBackground: Color = Color(0xFF0E0F12)
    public val DarkSurface: Color = Color(0xFF1C1D21)
    public val DarkInk: Color = Color(0xFFF2F1EE)
    public val DarkInkVariant: Color = Color(0xFFA3A29D)
    public val DarkPrimary: Color = Color(0xFF8FA4FF)
    public val DarkOnPrimary: Color = Color(0xFF0E0F12)
    public val DarkPrimaryContainer: Color = Color(0xFF1A2447)
    public val DarkOnPrimaryContainer: Color = Color(0xFFC7D2FF)
    public val DarkOutline: Color = Color(0xFF2C2D32)
    public val DarkSuccess: Color = Color(0xFF4CC38A)
    public val DarkError: Color = Color(0xFFF97066)
    public val DarkGlassSurface: Color = Color(0xD91C1D21)
    public val DarkGlassStroke: Color = Color(0x33FFFFFF)

    /** The paper a scanned page is drawn on; identical in both themes by design. */
    public val Paper: Color = Color(0xFFFFFFFF)

    // Camera overlay (design "Camera": always dark, over the live preview)
    public val CameraGlass: Color = Color(0x7A28282C)
    public val CameraGlassStroke: Color = Color(0x29FFFFFF)
    public val CameraOnGlass: Color = Color(0xFFFFFFFF)
    public val CameraOutline: Color = Color(0xFF6E86FF)
    public val CameraOutlineFill: Color = Color(0x246E86FF)
    public val CameraReady: Color = Color(0xFF4ADE80)
    public val CameraWarning: Color = Color(0xFFFFD84D)
}

/** Colors Material 3 has no slot for. */
@Immutable
public data class PpExtendedColors(
    val success: Color,
    val glassSurface: Color,
    val glassStroke: Color,
    val paper: Color,
)

public val LocalPpExtendedColors: androidx.compose.runtime.ProvidableCompositionLocal<PpExtendedColors> =
    staticCompositionLocalOf { lightExtendedColors() }

internal fun lightColors(): ColorScheme =
    lightColorScheme(
        primary = PpColors.LightPrimary,
        onPrimary = PpColors.LightOnPrimary,
        primaryContainer = PpColors.LightPrimaryContainer,
        onPrimaryContainer = PpColors.LightOnPrimaryContainer,
        secondary = PpColors.LightInkVariant,
        onSecondary = PpColors.LightSurface,
        background = PpColors.LightBackground,
        onBackground = PpColors.LightInk,
        surface = PpColors.LightSurface,
        onSurface = PpColors.LightInk,
        surfaceVariant = PpColors.LightBackground,
        onSurfaceVariant = PpColors.LightInkVariant,
        surfaceContainer = PpColors.LightSurface,
        surfaceContainerLow = PpColors.LightSurface,
        surfaceContainerHigh = PpColors.LightSurface,
        outline = PpColors.LightOutline,
        outlineVariant = PpColors.LightOutline,
        error = PpColors.LightError,
        onError = PpColors.LightSurface,
    )

internal fun darkColors(): ColorScheme =
    darkColorScheme(
        primary = PpColors.DarkPrimary,
        onPrimary = PpColors.DarkOnPrimary,
        primaryContainer = PpColors.DarkPrimaryContainer,
        onPrimaryContainer = PpColors.DarkOnPrimaryContainer,
        secondary = PpColors.DarkInkVariant,
        onSecondary = PpColors.DarkBackground,
        background = PpColors.DarkBackground,
        onBackground = PpColors.DarkInk,
        surface = PpColors.DarkSurface,
        onSurface = PpColors.DarkInk,
        surfaceVariant = PpColors.DarkBackground,
        onSurfaceVariant = PpColors.DarkInkVariant,
        surfaceContainer = PpColors.DarkSurface,
        surfaceContainerLow = PpColors.DarkSurface,
        surfaceContainerHigh = PpColors.DarkSurface,
        outline = PpColors.DarkOutline,
        outlineVariant = PpColors.DarkOutline,
        error = PpColors.DarkError,
        onError = PpColors.DarkBackground,
    )

internal fun lightExtendedColors(): PpExtendedColors =
    PpExtendedColors(
        success = PpColors.LightSuccess,
        glassSurface = PpColors.LightGlassSurface,
        glassStroke = PpColors.LightGlassStroke,
        paper = PpColors.Paper,
    )

internal fun darkExtendedColors(): PpExtendedColors =
    PpExtendedColors(
        success = PpColors.DarkSuccess,
        glassSurface = PpColors.DarkGlassSurface,
        glassStroke = PpColors.DarkGlassStroke,
        paper = PpColors.Paper,
    )
