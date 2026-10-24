package app.plainpaper.core.capture.di

import app.plainpaper.core.capture.background.BackgroundTask
import app.plainpaper.core.capture.background.IosBackgroundTask
import app.plainpaper.core.capture.camera.AvCameraControllerFactory
import app.plainpaper.core.capture.camera.AvCameraPreviewRenderer
import app.plainpaper.core.capture.camera.CameraControllerFactory
import app.plainpaper.core.capture.camera.CameraPreviewRenderer
import app.plainpaper.core.capture.files.ImageFiles
import app.plainpaper.core.capture.files.IosImageFiles
import app.plainpaper.core.capture.files.IosThumbnailLoader
import app.plainpaper.core.capture.files.ThumbnailLoader
import app.plainpaper.core.capture.permission.CameraPermissionFactory
import app.plainpaper.core.capture.permission.IosCameraPermissionFactory
import app.plainpaper.core.capture.picker.PhPhotoPickerFactory
import app.plainpaper.core.capture.picker.PhotoPickerFactory
import app.plainpaper.core.capture.region.IosRegionProvider
import app.plainpaper.core.capture.scanner.DocumentScannerFactory
import app.plainpaper.core.capture.scanner.VisionKitDocumentScannerFactory
import app.plainpaper.core.domain.platform.RegionProvider
import org.koin.core.module.Module
import org.koin.dsl.module

public actual val capturePlatformModule: Module =
    module {
        single<RegionProvider> { IosRegionProvider() }
        single<BackgroundTask> { IosBackgroundTask() }
        single<ImageFiles> { IosImageFiles(get()) }
        single<ThumbnailLoader> { IosThumbnailLoader(get()) }
        single<PhotoPickerFactory> { PhPhotoPickerFactory() }
        single<DocumentScannerFactory> { VisionKitDocumentScannerFactory() }
        single<CameraControllerFactory> { AvCameraControllerFactory(get()) }
        single<CameraPreviewRenderer> { AvCameraPreviewRenderer() }
        single<CameraPermissionFactory> { IosCameraPermissionFactory() }
    }
