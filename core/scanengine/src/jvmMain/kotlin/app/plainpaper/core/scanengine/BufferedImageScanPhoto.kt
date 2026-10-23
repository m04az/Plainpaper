@file:Suppress("MagicNumber") // wire format / pixel layout constants are the contract, not tunables

package app.plainpaper.core.scanengine

import java.awt.image.BufferedImage

/** Host photo; converted to RGBA bytes for the native call. */
public class BufferedImageScanPhoto(
    public val image: BufferedImage,
) : ScanPhoto {
    override val width: Int get() = image.width
    override val height: Int get() = image.height

    public fun toRgbaBytes(): ByteArray {
        val bytes = ByteArray(width * height * 4)
        val row = IntArray(width)
        for (y in 0 until height) {
            image.getRGB(0, y, width, 1, row, 0, width)
            val base = y * width * 4
            for (x in 0 until width) {
                val argb = row[x]
                val i = base + x * 4
                bytes[i] = (argb shr 16).toByte()
                bytes[i + 1] = (argb shr 8).toByte()
                bytes[i + 2] = argb.toByte()
                bytes[i + 3] = (argb ushr 24).toByte()
            }
        }
        return bytes
    }
}
