package app.plainpaper.core.domain.usecase

import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

public class ObservePreferencesUseCase(
    private val repository: PreferencesRepository,
) {
    public operator fun invoke(): Flow<AppPreferences> = repository.preferences.distinctUntilChanged()
}
