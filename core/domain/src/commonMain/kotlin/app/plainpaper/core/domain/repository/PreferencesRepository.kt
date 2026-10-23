package app.plainpaper.core.domain.repository

import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

public interface PreferencesRepository {
    /** Emits the current value immediately on collection and on every change. */
    public val preferences: Flow<AppPreferences>

    /** Completes only after the value is durably stored. */
    public suspend fun setLanguage(language: AppLanguage)

    /** Completes only after the value is durably stored. */
    public suspend fun setThemeMode(mode: ThemeMode)
}
