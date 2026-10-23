package app.plainpaper.core.scanengine

import android.graphics.Bitmap
import android.os.Build

/** A photo backed by a software ARGB_8888 [Bitmap] (hardware bitmaps cannot be locked). */
public class BitmapScanPhoto(
    source: Bitmap,
) : ScanPhoto {
    public val bitmap: Bitmap = source.asSoftwareArgb8888()

    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    private companion object {
        fun Bitmap.asSoftwareArgb8888(): Bitmap {
            val hardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE
            return if (config == Bitmap.Config.ARGB_8888 && !hardware) this else copy(Bitmap.Config.ARGB_8888, false)
        }
    }
}
