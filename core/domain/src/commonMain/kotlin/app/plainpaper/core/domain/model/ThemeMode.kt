package app.plainpaper.core.domain.model

public enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Whether the dark theme applies, given the device appearance for [ThemeMode.SYSTEM]. */
public fun ThemeMode.resolveIsDark(systemDark: Boolean): Boolean =
    when (this) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
