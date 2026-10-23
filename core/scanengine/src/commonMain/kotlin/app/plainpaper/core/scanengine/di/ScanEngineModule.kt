package app.plainpaper.core.scanengine.di

import app.plainpaper.core.scanengine.CapabilityProvider
import app.plainpaper.core.scanengine.DefaultScanEngine
import app.plainpaper.core.scanengine.PhotoDecoder
import app.plainpaper.core.scanengine.ScanEngine
import app.plainpaper.core.scanengine.bridge.NativeBridge
import app.plainpaper.core.scanengine.bridge.platformNativeBridge
import org.koin.core.module.Module
import org.koin.dsl.module

/** Engine wiring; [scanEnginePlatformModule] supplies [CapabilityProvider] and [PhotoDecoder]. */
public val scanEngineModule: Module =
    module {
        includes(scanEnginePlatformModule)
        single<NativeBridge> { platformNativeBridge() }
        single<ScanEngine> { DefaultScanEngine(bridge = get(), capabilityProvider = get(), dispatchers = get()) }
    }

public expect val scanEnginePlatformModule: Module
