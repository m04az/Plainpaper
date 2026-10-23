package app.plainpaper.di

import android.content.pm.PackageManager
import app.plainpaper.core.data.di.PREFERENCES_PATH_QUALIFIER
import app.plainpaper.core.data.preferences.PREFERENCES_FILE_NAME
import app.plainpaper.feature.settings.di.SETTINGS_APP_VERSION_QUALIFIER
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val androidPlatformModule: Module =
    module {
        single(named(PREFERENCES_PATH_QUALIFIER)) {
            androidContext()
                .filesDir
                .resolve("datastore")
                .resolve(PREFERENCES_FILE_NAME)
                .absolutePath
        }
        single(named(SETTINGS_APP_VERSION_QUALIFIER)) {
            val context = androidContext()
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
        }
        single<PackageManager> { androidContext().packageManager }
    }
