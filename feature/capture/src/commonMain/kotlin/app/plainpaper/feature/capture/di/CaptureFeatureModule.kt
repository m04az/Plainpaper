package app.plainpaper.feature.capture.di

import app.plainpaper.feature.capture.processing.PageProcessor
import org.koin.core.module.Module
import org.koin.dsl.module

/** Screens' ViewModels and the app-scoped [PageProcessor] (research R8). */
val captureFeatureModule: Module =
    module {
        single {
            PageProcessor(
                engine = get(),
                decoder = get(),
                files = get(),
                sessions = get(),
                background = get(),
                dispatchers = get(),
            )
        }
    }
