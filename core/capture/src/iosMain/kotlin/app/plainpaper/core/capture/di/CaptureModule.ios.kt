package app.plainpaper.core.capture.di

import app.plainpaper.core.capture.background.BackgroundTask
import app.plainpaper.core.capture.background.IosBackgroundTask
import app.plainpaper.core.capture.files.ImageFiles
import app.plainpaper.core.capture.files.IosImageFiles
import app.plainpaper.core.capture.files.IosThumbnailLoader
import app.plainpaper.core.capture.files.ThumbnailLoader
import app.plainpaper.core.capture.picker.PhPhotoPickerFactory
import app.plainpaper.core.capture.picker.PhotoPickerFactory
import app.plainpaper.core.capture.region.IosRegionProvider
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
    }
