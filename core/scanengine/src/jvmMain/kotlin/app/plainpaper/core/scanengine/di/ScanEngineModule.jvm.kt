package app.plainpaper.core.scanengine.di

import app.plainpaper.core.scanengine.CapabilityProvider
import app.plainpaper.core.scanengine.HostCapabilityProvider
import app.plainpaper.core.scanengine.ImageIoPhotoDecoder
import app.plainpaper.core.scanengine.PhotoDecoder
import org.koin.core.module.Module
import org.koin.dsl.module

public actual val scanEnginePlatformModule: Module =
    module {
        single<CapabilityProvider> { HostCapabilityProvider() }
        single<PhotoDecoder> { ImageIoPhotoDecoder(dispatchers = get()) }
    }
