package app.plainpaper.core.scanengine.di

import app.plainpaper.core.scanengine.CapabilityProvider
import app.plainpaper.core.scanengine.IosCapabilityProvider
import app.plainpaper.core.scanengine.IosPhotoDecoder
import app.plainpaper.core.scanengine.PhotoDecoder
import org.koin.core.module.Module
import org.koin.dsl.module

public actual val scanEnginePlatformModule: Module =
    module {
        single<CapabilityProvider> { IosCapabilityProvider() }
        single<PhotoDecoder> { IosPhotoDecoder(dispatchers = get()) }
    }
