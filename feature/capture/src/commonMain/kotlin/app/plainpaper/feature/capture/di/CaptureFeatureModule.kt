package app.plainpaper.feature.capture.di

import app.plainpaper.feature.capture.camera.CameraViewModel
import app.plainpaper.feature.capture.handoff.HandoffViewModel
import app.plainpaper.feature.capture.processing.PageProcessor
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
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
        viewModel {
            CameraViewModel(
                cameraFactory = get(),
                engine = get(),
                processor = get(),
                startSession = get(),
                addPage = get(),
                finishSession = get(),
                discardSession = get(),
                observeSession = get(),
                sessionDirectory = get(),
                resolvePageSize = get(),
                settings = get(),
                files = get(),
                thumbnails = get(),
                dispatchers = get(),
            )
        }
        viewModel { (sessionId: String) ->
            HandoffViewModel(
                sessionId = sessionId,
                observeSession = get(),
                finishSession = get(),
                thumbnails = get(),
                dispatchers = get(),
            )
        }
    }
