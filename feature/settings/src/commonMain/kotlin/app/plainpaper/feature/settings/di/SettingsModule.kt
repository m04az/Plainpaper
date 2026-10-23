package app.plainpaper.feature.settings.di

import app.plainpaper.feature.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier for the `String` app version; the platform module of the app provides it. */
const val SETTINGS_APP_VERSION_QUALIFIER = "appVersion"

val settingsModule: Module =
    module {
        viewModel {
            SettingsViewModel(
                observePreferences = get(),
                setLanguage = get(),
                setThemeMode = get(),
                dispatchers = get(),
                appVersion = get(named(SETTINGS_APP_VERSION_QUALIFIER)),
            )
        }
    }
