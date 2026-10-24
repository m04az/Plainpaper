package app.plainpaper.core.capture.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform capture services: `RegionProvider`, `BackgroundTask`, `ImageFiles`,
 * `CameraControllerFactory`, `CameraPreviewRenderer`, `CameraPermissionFactory`, `PhotoPickerFactory`,
 * `DocumentScannerFactory`.
 */
public val captureModule: Module =
    module {
        includes(capturePlatformModule)
    }

public expect val capturePlatformModule: Module
