package app.plainpaper.core.domain.usecase

import app.cash.turbine.test
import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.model.ThemeMode
import app.plainpaper.core.testing.FakePreferencesRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencesUseCasesTest {
    private val repository = FakePreferencesRepository()

    @Test
    fun observePreferences_emitsRepositoryValues() =
        runTest {
            ObservePreferencesUseCase(repository)().test {
                assertEquals(AppPreferences(), awaitItem())
                repository.setLanguage(AppLanguage.ARABIC)
                assertEquals(AppPreferences(language = AppLanguage.ARABIC), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observePreferences_suppressesConsecutiveDuplicates() =
        runTest {
            ObservePreferencesUseCase(repository)().test {
                assertEquals(AppPreferences(), awaitItem())
                repository.state.value = AppPreferences()
                repository.state.value = AppPreferences(themeMode = ThemeMode.DARK)
                assertEquals(AppPreferences(themeMode = ThemeMode.DARK), awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun setLanguage_writesWhenValueDiffers() =
        runTest {
            SetLanguageUseCase(repository)(AppLanguage.ARABIC)
            assertEquals(listOf(AppLanguage.ARABIC), repository.languageCalls)
            assertEquals(AppLanguage.ARABIC, repository.state.value.language)
        }

    @Test
    fun setLanguage_isNoOpWhenValueIsUnchanged() =
        runTest {
            SetLanguageUseCase(repository)(AppLanguage.SYSTEM)
            assertEquals(emptyList<AppLanguage>(), repository.languageCalls)
        }

    @Test
    fun setThemeMode_writesWhenValueDiffers() =
        runTest {
            SetThemeModeUseCase(repository)(ThemeMode.DARK)
            assertEquals(listOf(ThemeMode.DARK), repository.themeModeCalls)
        }

    @Test
    fun setThemeMode_isNoOpWhenValueIsUnchanged() =
        runTest {
            SetThemeModeUseCase(repository)(ThemeMode.SYSTEM)
            assertEquals(emptyList<ThemeMode>(), repository.themeModeCalls)
        }
}
