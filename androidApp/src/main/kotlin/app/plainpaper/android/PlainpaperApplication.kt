package app.plainpaper.android

import android.app.Application
import app.plainpaper.di.androidPlatformModule
import app.plainpaper.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PlainpaperApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PlainpaperApplication)
            modules(appModules(androidPlatformModule))
        }
    }
}
