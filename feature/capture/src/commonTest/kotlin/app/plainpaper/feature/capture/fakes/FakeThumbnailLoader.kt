package app.plainpaper.feature.capture.fakes

import androidx.compose.ui.graphics.ImageBitmap
import app.plainpaper.core.capture.files.ThumbnailLoader

/** Records requested paths; host tests cannot create an [ImageBitmap], so it returns null. */
class FakeThumbnailLoader : ThumbnailLoader {
    val requested = mutableListOf<String>()

    override suspend fun load(
        path: String,
        maxEdge: Int,
    ): ImageBitmap? {
        requested += path
        return null
    }
}
