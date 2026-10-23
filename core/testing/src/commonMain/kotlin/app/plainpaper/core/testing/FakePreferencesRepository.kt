package app.plainpaper.core.testing

import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.model.ThemeMode
import app.plainpaper.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [PreferencesRepository] that records every write. */
class FakePreferencesRepository(
    initial: AppPreferences = AppPreferences(),
) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    val languageCalls = mutableListOf<AppLanguage>()
    val themeModeCalls = mutableListOf<ThemeMode>()

    override val preferences = state

    override suspend fun setLanguage(language: AppLanguage) {
        languageCalls += language
        state.update { it.copy(language = language) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeModeCalls += mode
        state.update { it.copy(themeMode = mode) }
    }
}
