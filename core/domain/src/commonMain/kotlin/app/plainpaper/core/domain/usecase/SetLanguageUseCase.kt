package app.plainpaper.core.domain.usecase

import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first

public class SetLanguageUseCase(
    private val repository: PreferencesRepository,
) {
    public suspend operator fun invoke(language: AppLanguage) {
        if (repository.preferences.first().language != language) {
            repository.setLanguage(language)
        }
    }
}
