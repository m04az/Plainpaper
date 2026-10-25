package app.plainpaper.feature.capture.fakes

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import app.plainpaper.core.capture.files.OriginalPhoto
import app.plainpaper.core.capture.files.OriginalPhotoLoader

/** Returns a stub bitmap (host tests cannot create real ones) with a chosen decoded size. */
class FakeOriginalPhotoLoader(
    var decodedWidth: Int = 4000,
    var decodedHeight: Int = 3000,
    var fail: Boolean = false,
) : OriginalPhotoLoader {
    val requested = mutableListOf<String>()

    private val stub =
        object : ImageBitmap {
            override val colorSpace: ColorSpace get() = error("stub")
            override val config: ImageBitmapConfig get() = error("stub")
            override val hasAlpha: Boolean get() = false
            override val height: Int get() = 1
            override val width: Int get() = 1

            override fun prepareToDraw() = Unit

            override fun readPixels(
                buffer: IntArray,
                startX: Int,
                startY: Int,
                width: Int,
                height: Int,
                bufferOffset: Int,
                stride: Int,
            ) = Unit
        }

    override suspend fun load(
        path: String,
        maxEdge: Int,
        maxPixels: Int,
    ): OriginalPhoto? {
        requested += path
        return if (fail) null else OriginalPhoto(stub, decodedWidth, decodedHeight)
    }
}
