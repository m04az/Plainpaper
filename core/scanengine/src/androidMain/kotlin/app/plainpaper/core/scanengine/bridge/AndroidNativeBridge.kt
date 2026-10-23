@file:Suppress("MagicNumber") // wire format / pixel layout constants are the contract, not tunables

package app.plainpaper.core.scanengine.bridge

import android.graphics.Bitmap
import app.plainpaper.core.scanengine.BitmapScanOutput
import app.plainpaper.core.scanengine.BitmapScanPhoto
import app.plainpaper.core.scanengine.ScanPhoto

/** JNI transport over `android.graphics.Bitmap` (read in place with `AndroidBitmap_lockPixels`). */
internal class AndroidNativeBridge : JniNativeBridge() {
    override fun analyzeNative(
        engine: Long,
        photo: ScanPhoto,
        capability: Int,
        workingLongEdge: Int,
        allowFlatten: Boolean,
        userCorners: FloatArray?,
        cancel: Long,
        outStatus: IntArray,
    ): Long {
        val bitmap = (photo as? BitmapScanPhoto)?.bitmap
        if (bitmap == null) {
            outStatus[0] = NativeStatus.InvalidInput.code
            return 0L
        }
        return JniScanBridge.nativeAnalyzeBitmap(
            engine,
            bitmap,
            capability,
            workingLongEdge,
            allowFlatten,
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
        // Scan/Grayscale are single-channel (ALPHA_8 carries the grey levels); the rest RGBA.
        val channels = if (style <= 1) 1 else 4
        val config = if (channels == 1) Bitmap.Config.ALPHA_8 else Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(width, height, config)
        val outInfo = RenderMeta.newBuffer()
        val status = JniScanBridge.nativeRenderToBitmap(session, style, fit, width, height, cancel, bitmap, outInfo)
        val meta = RenderMeta.decode(outInfo)
        if (status != NativeStatus.Ok.code || meta.status != NativeStatus.Ok) {
            bitmap.recycle()
            return NativeRenderResult.Failure(NativeStatus.fromCode(status), JniScanBridge.nativeLastError())
        }
        return NativeRenderResult.Ready(
            BitmapScanOutput(
                bitmap = bitmap,
                channels = meta.channels,
                styleUsed = meta.styleUsed,
                fallbackReason = meta.fallbackReason,
                steps = meta.steps,
            ),
        )
    }
}
