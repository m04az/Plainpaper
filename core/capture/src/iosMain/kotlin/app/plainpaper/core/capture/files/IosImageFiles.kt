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
import platform.CoreGraphics.CGAffineTransformMake
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
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
private const val QUARTER_TURN = 90
private const val HALF_TURN = 180
private const val THREE_QUARTER_TURN = 270
private const val FULL_TURN = 360
private const val BITS_PER_COMPONENT = 8

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

/**
 * Mirrors [image] left to right when [mirrored], then rotates it clockwise by [rotation]
 * (multiples of 90), by drawing it into a bitmap context of the rotated size. CoreGraphics
 * has its origin bottom-left (y up), so each case below is the affine map from the image
 * rectangle `[0,w]x[0,h]` to the output rectangle in that space.
 */
@Suppress("MagicNumber") // affine matrices are literals
@OptIn(ExperimentalForeignApi::class)
private fun transformedImage(
    image: CGImageRef,
    width: Int,
    height: Int,
    rotation: Int,
    mirrored: Boolean,
): UIImage? {
    val w = width.toDouble()
    val h = height.toDouble()
    val turn = ((rotation % FULL_TURN) + FULL_TURN) % FULL_TURN
    val swap = turn == QUARTER_TURN || turn == THREE_QUARTER_TURN
    val outWidth = if (swap) height else width
    val outHeight = if (swap) width else height
    // Mirror first: x' = w - x. Matrices are [a, b, c, d, tx, ty] with x' = a*x + c*y + tx, y' = b*x + d*y + ty.
    val mirror =
        if (mirrored) {
            doubleArrayOf(
                -1.0,
                0.0,
                0.0,
                1.0,
                w,
                0.0,
            )
        } else {
            doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        }
    val turnMatrix =
        when (turn) {
            QUARTER_TURN -> doubleArrayOf(0.0, -1.0, 1.0, 0.0, 0.0, w)
            HALF_TURN -> doubleArrayOf(-1.0, 0.0, 0.0, -1.0, w, h)
            THREE_QUARTER_TURN -> doubleArrayOf(0.0, 1.0, -1.0, 0.0, h, 0.0)
            else -> doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        }
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val context =
        CGBitmapContextCreate(
            null,
            outWidth.toULong(),
            outHeight.toULong(),
            BITS_PER_COMPONENT.toULong(),
            0uL,
            colorSpace,
            CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
        )
    CGColorSpaceRelease(colorSpace)
    if (context == null) return null
    // Turn after mirror: result = turnMatrix(mirror(p)).
    CGContextConcatCTM(
        context,
        CGAffineTransformMake(
            turnMatrix[0] * mirror[0] + turnMatrix[2] * mirror[1],
            turnMatrix[1] * mirror[0] + turnMatrix[3] * mirror[1],
            turnMatrix[0] * mirror[2] + turnMatrix[2] * mirror[3],
            turnMatrix[1] * mirror[2] + turnMatrix[3] * mirror[3],
            turnMatrix[0] * mirror[4] + turnMatrix[2] * mirror[5] + turnMatrix[4],
            turnMatrix[1] * mirror[4] + turnMatrix[3] * mirror[5] + turnMatrix[5],
        ),
    )
    CGContextDrawImage(context, CGRectMake(0.0, 0.0, w, h), image)
    val rendered = CGBitmapContextCreateImage(context)
    CGContextRelease(context)
    val result = rendered?.let { UIImage(cGImage = it) }
    if (rendered != null) CGImageRelease(rendered)
    return result
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
        rotation: Int,
        mirrored: Boolean,
    ): Boolean =
        withContext(dispatchers.io) {
            val cgOutput = output as? CgImageScanOutput ?: return@withContext false
            val image =
                if (rotation % FULL_TURN == 0 && !mirrored) {
                    UIImage(cGImage = cgOutput.image)
                } else {
                    transformedImage(cgOutput.image, cgOutput.width, cgOutput.height, rotation, mirrored)
                        ?: return@withContext false
                }
            val data = UIImageJPEGRepresentation(image, quality / PERCENT) ?: return@withContext false
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
