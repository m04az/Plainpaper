package app.plainpaper.core.capture.files

import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.scanengine.CgImageScanOutput
import app.plainpaper.core.scanengine.ScanOutput
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFURLRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.CGImageSourceGetCount
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

private const val THUMBNAIL_QUALITY = 0.85
private const val PERCENT = 100.0

private const val THUMBNAIL_OPTIONS = 3

/** Shared ImageIO thumbnail decode (honours EXIF orientation through the transform option). */
@OptIn(ExperimentalForeignApi::class)
internal fun thumbnailImage(
    path: String,
    maxEdge: Int,
): UIImage? {
    val url: CFURLRef = CFBridgingRetain(NSURL.fileURLWithPath(path))?.reinterpret() ?: return null
    val source = CGImageSourceCreateWithURL(url, null)
    CFRelease(url)
    return source?.let { decodeThumbnail(it, maxEdge) }
}

@OptIn(ExperimentalForeignApi::class)
private fun decodeThumbnail(
    source: CGImageSourceRef,
    maxEdge: Int,
): UIImage? {
    val options =
        CFDictionaryCreateMutable(
            null,
            THUMBNAIL_OPTIONS.toLong(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
    val edge = intArrayOf(maxEdge)
    val number = edge.usePinned { CFNumberCreate(null, kCFNumberIntType, it.addressOf(0)) }
    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
    CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, number)
    val cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0uL, options)
    CFRelease(number)
    CFRelease(options)
    CFRelease(source)
    val image = cgImage?.let { UIImage(cGImage = it) }
    if (cgImage != null) CGImageRelease(cgImage)
    return image
}

/** CoreGraphics / ImageIO backed files (research R11). */
@OptIn(ExperimentalForeignApi::class)
internal class IosImageFiles(
    private val dispatchers: DispatcherProvider,
) : ImageFiles {
    override suspend fun writeJpeg(
        output: ScanOutput,
        path: String,
        quality: Int,
    ): Boolean =
        withContext(dispatchers.io) {
            val image = (output as? CgImageScanOutput)?.image ?: return@withContext false
            val data =
                UIImageJPEGRepresentation(UIImage(cGImage = image), quality / PERCENT) ?: return@withContext false
            data.writeToFile(path, atomically = true)
        }

    override suspend fun writeThumbnail(
        fromPath: String,
        toPath: String,
        maxEdge: Int,
    ): Boolean =
        withContext(dispatchers.io) {
            val thumbnail = thumbnailImage(fromPath, maxEdge) ?: return@withContext false
            val data = UIImageJPEGRepresentation(thumbnail, THUMBNAIL_QUALITY) ?: return@withContext false
            data.writeToFile(toPath, atomically = true)
        }

    override suspend fun copyOriginal(
        uri: String,
        destinationPath: String,
    ): CopyResult =
        withContext(dispatchers.io) {
            val manager = NSFileManager.defaultManager
            val source = uri.removePrefix("file://")
            if (!manager.fileExistsAtPath(source)) return@withContext CopyResult.Failed(CopyFailure.NotFound)
            manager.removeItemAtPath(destinationPath, error = null)
            if (!manager.copyItemAtPath(source, toPath = destinationPath, error = null)) {
                return@withContext CopyResult.Failed(CopyFailure.Io)
            }
            if (!isImage(destinationPath)) {
                manager.removeItemAtPath(destinationPath, error = null)
                return@withContext CopyResult.Failed(CopyFailure.NotAnImage)
            }
            val size =
                (manager.attributesOfItemAtPath(destinationPath, error = null)?.get(NSFileSize) as? NSNumber)?.longValue
                    ?: 0L
            CopyResult.Copied(destinationPath, size)
        }

    override fun exists(path: String): Boolean {
        val size =
            (
                NSFileManager.defaultManager
                    .attributesOfItemAtPath(
                        path,
                        error = null,
                    )?.get(NSFileSize) as? NSNumber
            )?.longValue
        return (size ?: 0L) > 0L
    }

    override fun freeBytes(directory: String): Long {
        val attributes = NSFileManager.defaultManager.attributesOfFileSystemForPath(directory, error = null)
        return (attributes?.get(NSFileSystemFreeSize) as? NSNumber)?.longValue ?: Long.MAX_VALUE
    }

    private fun isImage(path: String): Boolean {
        val url: CFURLRef = CFBridgingRetain(NSURL.fileURLWithPath(path))?.reinterpret() ?: return false
        val source = CGImageSourceCreateWithURL(url, null)
        CFRelease(url)
        if (source == null) return false
        val ok = CGImageSourceGetCount(source) > 0uL
        CFRelease(source)
        return ok
    }
}
