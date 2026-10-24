@file:Suppress("MagicNumber") // wire format / pixel layout constants are the contract, not tunables

package app.plainpaper.core.scanengine.bridge

import app.plainpaper.core.scanengine.BufferedImageScanOutput
import app.plainpaper.core.scanengine.BufferedImageScanPhoto
import app.plainpaper.core.scanengine.ScanPhoto
import java.awt.image.BufferedImage

/** JNI transport over byte arrays for the host quality suite. */
internal class JvmNativeBridge : JniNativeBridge() {
    override fun analyzeNative(
        engine: Long,
        photo: ScanPhoto,
        capability: Int,
        workingLongEdge: Int,
        allowFlatten: Boolean,
        keepOrientation: Boolean,
        userCorners: FloatArray?,
        cancel: Long,
        outStatus: IntArray,
    ): Long {
        val image = photo as? BufferedImageScanPhoto
        if (image == null) {
            outStatus[0] = NativeStatus.InvalidInput.code
            return 0L
        }
        return JniScanBridge.nativeAnalyzeBytes(
            engine,
            image.toRgbaBytes(),
            image.width,
            image.height,
            image.width * 4,
            capability,
            workingLongEdge,
            allowFlatten,
            keepOrientation,
            userCorners,
            cancel,
            outStatus,
        )
    }

    override fun renderNative(
        session: Long,
        style: Int,
        fit: Int,
        width: Int,
        height: Int,
        cancel: Long,
    ): NativeRenderResult {
        val outInfo = RenderMeta.newBuffer()
        val bytes = JniScanBridge.nativeRenderToBytes(session, style, fit, width, height, cancel, outInfo)
        val meta = RenderMeta.decode(outInfo)
        if (meta.status != NativeStatus.Ok || bytes == null) {
            return NativeRenderResult.Failure(meta.status, JniScanBridge.nativeLastError())
        }
        val image = toBufferedImage(bytes, width, height, meta.channels)
        return NativeRenderResult.Ready(
            BufferedImageScanOutput(image, meta.channels, meta.styleUsed, meta.fallbackReason, meta.steps),
        )
    }

    private fun toBufferedImage(
        bytes: ByteArray,
        width: Int,
        height: Int,
        channels: Int,
    ): BufferedImage {
        if (channels == 1) {
            val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
            image.raster.setDataElements(0, 0, width, height, bytes)
            return image
        }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val row = IntArray(width)
        for (y in 0 until height) {
            val base = y * width * 4
            for (x in 0 until width) {
                val i = base + x * 4
                val r = bytes[i].toInt() and 0xFF
                val g = bytes[i + 1].toInt() and 0xFF
                val b = bytes[i + 2].toInt() and 0xFF
                val a = bytes[i + 3].toInt() and 0xFF
                row[x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            image.setRGB(0, y, width, 1, row, 0, width)
        }
        return image
    }
}
