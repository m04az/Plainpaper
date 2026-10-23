package app.plainpaper

import app.plainpaper.di.appModules
import app.plainpaper.di.iosPlatformModule
import org.koin.core.context.startKoin

/** Called once from `iOSApp.swift` before the first view is created. */
fun initKoin() {
    startKoin {
        modules(appModules(iosPlatformModule))
    }
}
