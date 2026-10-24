package app.plainpaper.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.plainpaper.core.data.capture.DataStoreCaptureSettingsRepository
import app.plainpaper.core.data.capture.FileCaptureSessionRepository
import app.plainpaper.core.data.coroutines.DefaultDispatcherProvider
import app.plainpaper.core.data.platform.currentTimeMillis
import app.plainpaper.core.data.platform.systemFileSystem
import app.plainpaper.core.data.preferences.DataStorePreferencesRepository
import app.plainpaper.core.data.preferences.createPreferencesDataStore
import app.plainpaper.core.data.pro.FreeOnlyProStatusRepository
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.repository.CaptureSessionRepository
import app.plainpaper.core.domain.repository.CaptureSettingsRepository
import app.plainpaper.core.domain.repository.PreferencesRepository
import app.plainpaper.core.domain.repository.ProStatusRepository
import app.plainpaper.core.domain.usecase.ObservePreferencesUseCase
import app.plainpaper.core.domain.usecase.SetLanguageUseCase
import app.plainpaper.core.domain.usecase.SetThemeModeUseCase
import app.plainpaper.core.domain.usecase.capture.AddCapturedPageUseCase
import app.plainpaper.core.domain.usecase.capture.DiscardSessionUseCase
import app.plainpaper.core.domain.usecase.capture.EvaluateImportLimitUseCase
import app.plainpaper.core.domain.usecase.capture.FinishSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ObserveActiveSessionUseCase
import app.plainpaper.core.domain.usecase.capture.RecoverSessionUseCase
import app.plainpaper.core.domain.usecase.capture.ResolveDefaultPageSizeUseCase
import app.plainpaper.core.domain.usecase.capture.StartSessionUseCase
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

/**
 * Qualifier for the `String` absolute directory that holds capture sessions (contracts/storage.md).
 * Platform modules provide it (Android: `filesDir/capture`, iOS: Documents/capture).
 */
public const val CAPTURE_ROOT_QUALIFIER: String = "captureRoot"

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

        // Capture & import (003)
        single<CaptureSessionRepository> {
            FileCaptureSessionRepository(
                fileSystem = systemFileSystem(),
                rootPath = get(named(CAPTURE_ROOT_QUALIFIER)),
                now = ::currentTimeMillis,
            )
        }
        single<CaptureSettingsRepository> { DataStoreCaptureSettingsRepository(get()) }
        single<ProStatusRepository> { FreeOnlyProStatusRepository() }
        factory { ObserveActiveSessionUseCase(get()) }
        factory { StartSessionUseCase(get()) }
        factory { AddCapturedPageUseCase(get()) }
        factory { FinishSessionUseCase(get()) }
        factory { DiscardSessionUseCase(get()) }
        factory { RecoverSessionUseCase(get()) }
        factory { EvaluateImportLimitUseCase(get()) }
        factory { ResolveDefaultPageSizeUseCase(get()) }
    }
