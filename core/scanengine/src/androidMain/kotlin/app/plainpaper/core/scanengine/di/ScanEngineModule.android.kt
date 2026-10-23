package app.plainpaper.core.scanengine.di

import android.content.Context
import app.plainpaper.core.scanengine.AndroidCapabilityProvider
import app.plainpaper.core.scanengine.AndroidPhotoDecoder
import app.plainpaper.core.scanengine.CapabilityProvider
import app.plainpaper.core.scanengine.PhotoDecoder
import org.koin.core.module.Module
import org.koin.dsl.module

public actual val scanEnginePlatformModule: Module =
    module {
        single<CapabilityProvider> { AndroidCapabilityProvider(get<Context>()) }
        single<PhotoDecoder> { AndroidPhotoDecoder(dispatchers = get()) }
    }
