package app.plainpaper

import androidx.compose.runtime.Composable

/**
 * Keeps system bar icons readable when the app theme differs from the device theme
 * (for example a dark app on a light device). No-op where the platform handles it.
 */
@Composable
expect fun SystemBarsAppearance(isDark: Boolean)
