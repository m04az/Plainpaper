@file:Suppress("MagicNumber") // wire format / pixel layout constants are the contract, not tunables

package app.plainpaper.core.scanengine

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGImageRetain
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big

/** A photo backed by a `CGImage` (retained for the lifetime of this object). */
@OptIn(ExperimentalForeignApi::class)
public class CgImageScanPhoto(
    image: CGImageRef,
) : ScanPhoto {
    public val image: CGImageRef = CGImageRetain(image) ?: image

    override val width: Int = CGImageGetWidth(image).toInt()
    override val height: Int = CGImageGetHeight(image).toInt()

    /** RGBA8888 copy rendered through a bitmap context (alpha is opaque for photos). */
    public fun toRgbaBytes(): ByteArray {
        val bytes = ByteArray(width * height * 4)
        bytes.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val context =
                CGBitmapContextCreate(
                    data = pinned.addressOf(0),
                    width = width.toULong(),
                    height = height.toULong(),
                    bitsPerComponent = 8u,
                    bytesPerRow = (width * 4).toULong(),
                    space = colorSpace,
                    bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
                )
            CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), image)
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
        }
        return bytes
    }

    public fun release() {
        CGImageRelease(image)
    }
}
