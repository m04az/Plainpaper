package app.plainpaper.core.capture.di

import app.plainpaper.core.capture.background.AndroidBackgroundTask
import app.plainpaper.core.capture.background.BackgroundTask
import app.plainpaper.core.capture.camera.CameraControllerFactory
import app.plainpaper.core.capture.camera.CameraPreviewRenderer
import app.plainpaper.core.capture.camera.CameraXControllerFactory
import app.plainpaper.core.capture.camera.CameraXPreviewRenderer
import app.plainpaper.core.capture.files.AndroidImageFiles
import app.plainpaper.core.capture.files.AndroidThumbnailLoader
import app.plainpaper.core.capture.files.ImageFiles
import app.plainpaper.core.capture.files.ThumbnailLoader
import app.plainpaper.core.capture.permission.AndroidCameraPermissionFactory
import app.plainpaper.core.capture.permission.CameraPermissionFactory
import app.plainpaper.core.capture.region.AndroidRegionProvider
import app.plainpaper.core.domain.platform.RegionProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

public actual val capturePlatformModule: Module =
    module {
        single<RegionProvider> { AndroidRegionProvider() }
        single<BackgroundTask> { AndroidBackgroundTask() }
        single<ImageFiles> { AndroidImageFiles(androidContext(), get()) }
        single<ThumbnailLoader> { AndroidThumbnailLoader(get()) }
        single<CameraControllerFactory> { CameraXControllerFactory(androidContext(), get()) }
        single<CameraPreviewRenderer> { CameraXPreviewRenderer() }
        single<CameraPermissionFactory> { AndroidCameraPermissionFactory() }
    }
