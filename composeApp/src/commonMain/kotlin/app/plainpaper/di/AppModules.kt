package app.plainpaper.di

import app.plainpaper.core.data.di.dataModule
import app.plainpaper.feature.home.di.homeModule
import app.plainpaper.feature.settings.di.settingsModule
import org.koin.core.module.Module

/** Qualifier for the `String` app version shown in Settings; provided by platform modules. */
const val APP_VERSION_QUALIFIER = "appVersion"

fun appModules(platformModule: Module): List<Module> =
    listOf(platformModule, dataModule, homeModule, settingsModule)
