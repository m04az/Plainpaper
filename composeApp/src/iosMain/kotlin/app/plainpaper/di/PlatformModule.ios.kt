package app.plainpaper.di

import app.plainpaper.core.data.di.PREFERENCES_PATH_QUALIFIER
import app.plainpaper.core.data.preferences.PREFERENCES_FILE_NAME
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

val iosPlatformModule: Module =
    module {
        single(named(PREFERENCES_PATH_QUALIFIER)) { "${documentsDirectory()}/datastore/$PREFERENCES_FILE_NAME" }
        single(named(APP_VERSION_QUALIFIER)) {
            NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: ""
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun documentsDirectory(): String {
    val url =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return requireNotNull(url?.path) { "Documents directory unavailable" }
}
