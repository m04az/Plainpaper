package app.plainpaper.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.plainpaper.core.data.coroutines.DefaultDispatcherProvider
import app.plainpaper.core.data.preferences.DataStorePreferencesRepository
import app.plainpaper.core.data.preferences.createPreferencesDataStore
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.repository.PreferencesRepository
import app.plainpaper.core.domain.usecase.ObservePreferencesUseCase
import app.plainpaper.core.domain.usecase.SetLanguageUseCase
import app.plainpaper.core.domain.usecase.SetThemeModeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Qualifier for the `String` absolute path of the preferences file. Platform modules
 * provide it (Android: `filesDir/datastore/<name>`, iOS: Documents/datastore/<name>).
 */
public const val PREFERENCES_PATH_QUALIFIER: String = "preferencesPath"

public val dataModule: Module =
    module {
        single<DispatcherProvider> { DefaultDispatcherProvider() }
        single<DataStore<Preferences>> {
            val dispatchers = get<DispatcherProvider>()
            createPreferencesDataStore(
                path = get(named(PREFERENCES_PATH_QUALIFIER)),
                scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            )
        }
        single<PreferencesRepository> { DataStorePreferencesRepository(get()) }
        factory { ObservePreferencesUseCase(get()) }
        factory { SetLanguageUseCase(get()) }
        factory { SetThemeModeUseCase(get()) }
    }
