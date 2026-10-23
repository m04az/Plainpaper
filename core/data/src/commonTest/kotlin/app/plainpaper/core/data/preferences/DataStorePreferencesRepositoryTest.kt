package app.plainpaper.core.data.preferences

import app.cash.turbine.test
import app.plainpaper.core.data.tempDirectoryPath
import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStorePreferencesRepositoryTest {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val path =
        tempDirectoryPath().trimEnd('/') +
            "/plainpaper-test-${Random.nextLong()}/$PREFERENCES_FILE_NAME"
    private val repository = DataStorePreferencesRepository(createPreferencesDataStore(path, scope))

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun freshStoreEmitsDefaults() =
        runTest {
            assertEquals(AppPreferences(), repository.preferences.first())
        }

    @Test
    fun setLanguagePersists() =
        runTest {
            repository.setLanguage(AppLanguage.ARABIC)
            assertEquals(AppLanguage.ARABIC, repository.preferences.first().language)
        }

    @Test
    fun setThemeModePersists() =
        runTest {
            repository.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, repository.preferences.first().themeMode)
        }

    @Test
    fun writingOneKeyKeepsTheOther() =
        runTest {
            repository.setLanguage(AppLanguage.ENGLISH)
            repository.setThemeMode(ThemeMode.LIGHT)
            assertEquals(
                AppPreferences(language = AppLanguage.ENGLISH, themeMode = ThemeMode.LIGHT),
                repository.preferences.first(),
            )
        }

    @Test
    fun flowEmitsOnEveryChange() =
        runTest {
            repository.preferences.test {
                assertEquals(AppPreferences(), awaitItem())
                repository.setThemeMode(ThemeMode.DARK)
                assertEquals(ThemeMode.DARK, awaitItem().themeMode)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
