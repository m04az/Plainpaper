package app.plainpaper.core.domain.usecase

import app.plainpaper.core.domain.model.ThemeMode
import app.plainpaper.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first

public class SetThemeModeUseCase(
    private val repository: PreferencesRepository,
) {
    public suspend operator fun invoke(mode: ThemeMode) {
        if (repository.preferences.first().themeMode != mode) {
            repository.setThemeMode(mode)
        }
    }
}
