package app.plainpaper.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.model.ThemeMode
import app.plainpaper.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.IOException

public class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {
    override val preferences: Flow<AppPreferences> =
        dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }.map { it.toAppPreferences() }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[PreferencesKeys.LANGUAGE] = language.token }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[PreferencesKeys.THEME] = mode.token }
    }
}
