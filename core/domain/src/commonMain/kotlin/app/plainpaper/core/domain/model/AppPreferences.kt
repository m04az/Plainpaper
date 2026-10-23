package app.plainpaper.core.domain.model

/** The user's persisted choices. A fresh install sees the default instance. */
public data class AppPreferences(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
