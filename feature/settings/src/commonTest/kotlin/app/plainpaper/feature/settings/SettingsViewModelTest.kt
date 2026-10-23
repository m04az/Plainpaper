package app.plainpaper.feature.settings

import app.cash.turbine.test
import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.model.ThemeMode
import app.plainpaper.core.domain.usecase.ObservePreferencesUseCase
import app.plainpaper.core.domain.usecase.SetLanguageUseCase
import app.plainpaper.core.domain.usecase.SetThemeModeUseCase
import app.plainpaper.core.testing.FakePreferencesRepository
import app.plainpaper.core.testing.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakePreferencesRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        SettingsViewModel(
            observePreferences = ObservePreferencesUseCase(repository),
            setLanguage = SetLanguageUseCase(repository),
            setThemeMode = SetThemeModeUseCase(repository),
            dispatchers = TestDispatcherProvider(dispatcher),
            appVersion = "0.1.0",
        )

    @Test
    fun initialStateUsesDefaultsAndVersion() {
        assertEquals(SettingsUiState(appVersion = "0.1.0"), viewModel().uiState.value)
    }

    @Test
    fun stateReflectsRepositoryEmissions() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                assertEquals(AppLanguage.SYSTEM, awaitItem().language)
                repository.state.value = AppPreferences(language = AppLanguage.ARABIC, themeMode = ThemeMode.DARK)
                val updated = awaitItem()
                assertEquals(AppLanguage.ARABIC, updated.language)
                assertEquals(ThemeMode.DARK, updated.themeMode)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun languageRowOpensLanguageSheet() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(SettingsAction.LanguageRowClicked)
                assertEquals(SettingsSheet.LANGUAGE, awaitItem().openSheet)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun languageSelectedClosesSheetAndPersistsOnce() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(SettingsAction.LanguageRowClicked)
                awaitItem()
                viewModel.onAction(SettingsAction.LanguageSelected(AppLanguage.ARABIC))
                val closed = expectMostRecentItem()
                assertNull(closed.openSheet)
                assertEquals(AppLanguage.ARABIC, closed.language)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(AppLanguage.ARABIC), repository.languageCalls)
        }

    @Test
    fun sheetDismissedClosesSheet() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(SettingsAction.LanguageRowClicked)
                awaitItem()
                viewModel.onAction(SettingsAction.SheetDismissed)
                assertNull(awaitItem().openSheet)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun themeRowOpensThemeSheet() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(SettingsAction.ThemeRowClicked)
                assertEquals(SettingsSheet.THEME, awaitItem().openSheet)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun themeSelectedClosesSheetAndPersistsOnce() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(SettingsAction.ThemeRowClicked)
                awaitItem()
                viewModel.onAction(SettingsAction.ThemeSelected(ThemeMode.DARK))
                val closed = expectMostRecentItem()
                assertNull(closed.openSheet)
                assertEquals(ThemeMode.DARK, closed.themeMode)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(ThemeMode.DARK), repository.themeModeCalls)
        }
}
