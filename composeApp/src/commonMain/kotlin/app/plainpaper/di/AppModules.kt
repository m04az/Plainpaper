package app.plainpaper.di

import app.plainpaper.core.capture.di.captureModule
import app.plainpaper.core.data.di.dataModule
import app.plainpaper.core.scanengine.DetectorModelSource
import app.plainpaper.core.scanengine.di.scanEngineModule
import app.plainpaper.detector.ComposeResourceDetectorModelSource
import app.plainpaper.feature.capture.di.captureFeatureModule
import app.plainpaper.feature.home.di.homeModule
import app.plainpaper.feature.settings.di.settingsModule
import org.koin.core.module.Module
import org.koin.dsl.module

private val detectorModelModule: Module =
    module {
        single<DetectorModelSource> { ComposeResourceDetectorModelSource() }
    }

fun appModules(platformModule: Module): List<Module> =
    listOf(
        platformModule,
        dataModule,
        scanEngineModule,
        detectorModelModule,
        captureModule,
        captureFeatureModule,
        homeModule,
        settingsModule,
    )
