package app.plainpaper.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * Applies the Plainpaper look: Material 3 color scheme, type scale and shapes built from the
 * design tokens, plus the extended colors and spacing scale.
 *
 * @param darkTheme whether to use the dark scheme; callers resolve `ThemeMode` beforehand.
 * @param useArabicTypeface switches the UI typeface to IBM Plex Sans Arabic for Arabic.
 */
@Composable
public fun PlainpaperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useArabicTypeface: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(darkTheme) { if (darkTheme) darkColors() else lightColors() }
    val extended = remember(darkTheme) { if (darkTheme) darkExtendedColors() else lightExtendedColors() }
    val family = if (useArabicTypeface) plexArabicFontFamily() else interFontFamily()
    val typography = remember(family) { plainpaperTypography(family) }
    val shapes = remember { PpShapes() }
    val spacing = remember { Spacing() }

    CompositionLocalProvider(
        LocalPpExtendedColors provides extended,
        LocalPpShapes provides shapes,
        LocalSpacing provides spacing,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = materialShapes(shapes),
            content = content,
        )
    }
}

/** Accessors for the active theme values. */
public object PlainpaperTheme {
    public val colors: ColorScheme
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    public val typography: Typography
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography

    public val extended: PpExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalPpExtendedColors.current

    public val shapes: PpShapes
        @Composable @ReadOnlyComposable
        get() = LocalPpShapes.current

    public val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current
}
