package app.plainpaper.core.capture.files

import androidx.compose.ui.graphics.toComposeImageBitmap
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.scanengine.DecodeSizing
import app.plainpaper.core.scanengine.PhotoSize
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFURLRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.kCGImagePropertyOrientation
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/**
 * ImageIO thumbnail with the EXIF transform (the same call the engine's `IosPhotoDecoder`
 * uses), so the orientation matches. The decoded size is the file's oriented pixel size passed
 * through the same long-edge cap the decoder applies ([DecodeSizing.fitLongEdge]).
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosOriginalPhotoLoader(
    private val dispatchers: DispatcherProvider,
) : OriginalPhotoLoader {
    override suspend fun load(
        path: String,
        maxEdge: Int,
        maxPixels: Int,
    ): OriginalPhoto? =
        withContext(dispatchers.io) {
            val full = orientedPixelSize(path) ?: return@withContext null
            val decoded = DecodeSizing.fitLongEdge(full.width, full.height, DecodeSizing.thumbnailMaxEdge(maxPixels))
            val image = thumbnailImage(path, maxEdge) ?: return@withContext null
            val data = UIImagePNGRepresentation(image) ?: return@withContext null
            val bitmap =
                runCatching { Image.makeFromEncoded(data.toByteArray()).toComposeImageBitmap() }.getOrNull()
                    ?: return@withContext null
            OriginalPhoto(bitmap, decoded.width, decoded.height)
        }

    /** Pixel size with the EXIF orientation applied (orientations 5-8 swap width and height). */
    private fun orientedPixelSize(path: String): PhotoSize? {
        val url: CFURLRef = CFBridgingRetain(NSURL.fileURLWithPath(path))?.reinterpret() ?: return null
        val source = CGImageSourceCreateWithURL(url, null)
        CFRelease(url)
        if (source == null) return null
        val properties = CFBridgingRelease(CGImageSourceCopyPropertiesAtIndex(source, 0uL, null)) as? Map<*, *>
        CFRelease(source)
        val width = properties.intValue(kCGImagePropertyPixelWidth)
        val height = properties.intValue(kCGImagePropertyPixelHeight)
        val orientation = properties.intValue(kCGImagePropertyOrientation) ?: 1
        return if (width == null || height == null) {
            null
        } else if (orientation in TRANSPOSING_ORIENTATIONS) {
            PhotoSize(height, width)
        } else {
            PhotoSize(width, height)
        }
    }

    private fun Map<*, *>?.intValue(key: CFStringRef?): Int? =
        (this?.get(CFBridgingRelease(key)) as? NSNumber)?.intValue?.takeIf { it > 0 }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
        return bytes
    }

    private companion object {
        /** EXIF orientations that turn the image a quarter (transpose, rotate 90, transverse, rotate 270). */
        val TRANSPOSING_ORIENTATIONS = 5..8
    }
}
