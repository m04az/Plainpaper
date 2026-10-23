@file:Suppress("MagicNumber") // wire format / pixel layout constants are the contract, not tunables

package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.coroutines.DispatcherProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFURLRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * ImageIO thumbnail decoding: applies EXIF orientation, decodes HEIC natively and caps the pixel
 * count without decoding the full image first (research R12).
 */
@OptIn(ExperimentalForeignApi::class)
public class IosPhotoDecoder(
    private val dispatchers: DispatcherProvider,
) : PhotoDecoder {
    override suspend fun decode(
        path: String,
        maxPixels: Int,
    ): DecodeResult =
        withContext(dispatchers.io) {
            if (!NSFileManager.defaultManager.fileExistsAtPath(path)) {
                return@withContext DecodeResult.Failed(DecodeFailure.NotFound)
            }
            val url: CFURLRef? = CFBridgingRetain(NSURL.fileURLWithPath(path))?.reinterpret()
            val source = CGImageSourceCreateWithURL(url, null)
            if (url != null) CFRelease(url)
            if (source == null) return@withContext DecodeResult.Failed(DecodeFailure.UnsupportedFormat)
            // Max pixel size = the long edge of a maxPixels image with a 4:3 aspect (conservative).
            val maxEdge = ceil(sqrt(maxPixels.toDouble() * 4.0 / 3.0)).toInt()
            val options =
                mapOf<Any?, Any?>(
                    CFBridgingRelease(kCGImageSourceCreateThumbnailFromImageAlways) to true,
                    CFBridgingRelease(kCGImageSourceCreateThumbnailWithTransform) to true,
                    CFBridgingRelease(kCGImageSourceThumbnailMaxPixelSize) to NSNumber(int = maxEdge),
                )
            val cfOptions: CFDictionaryRef? = CFBridgingRetain(options)?.reinterpret()
            val image = CGImageSourceCreateThumbnailAtIndex(source, 0u, cfOptions)
            if (cfOptions != null) CFRelease(cfOptions)
            CFRelease(source)
            if (image == null) return@withContext DecodeResult.Failed(DecodeFailure.Corrupt)
            val photo = CgImageScanPhoto(image)
            platform.CoreGraphics.CGImageRelease(image)
            DecodeResult.Ready(photo)
        }
}
