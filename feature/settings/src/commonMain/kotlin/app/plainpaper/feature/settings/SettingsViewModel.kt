package app.plainpaper.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.usecase.ObservePreferencesUseCase
import app.plainpaper.core.domain.usecase.SetLanguageUseCase
import app.plainpaper.core.domain.usecase.SetThemeModeUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

class SettingsViewModel(
    observePreferences: ObservePreferencesUseCase,
    private val setLanguage: SetLanguageUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    private val dispatchers: DispatcherProvider,
    private val appVersion: String,
) : ViewModel() {
    private val openSheet = MutableStateFlow<SettingsSheet?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(observePreferences(), openSheet) { preferences, sheet ->
            SettingsUiState(
                language = preferences.language,
                themeMode = preferences.themeMode,
                openSheet = sheet,
                appVersion = appVersion,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState(appVersion = appVersion),
        )

    private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.LanguageRowClicked -> {
                openSheet.value = SettingsSheet.LANGUAGE
            }

            SettingsAction.ThemeRowClicked -> {
                openSheet.value = SettingsSheet.THEME
            }

            SettingsAction.SheetDismissed -> {
                openSheet.value = null
            }

            is SettingsAction.LanguageSelected -> {
                openSheet.value = null
                viewModelScope.launch(dispatchers.io) { setLanguage(action.language) }
            }

            is SettingsAction.ThemeSelected -> {
                openSheet.value = null
                viewModelScope.launch(dispatchers.io) { setThemeMode(action.mode) }
            }
        }
    }
}
