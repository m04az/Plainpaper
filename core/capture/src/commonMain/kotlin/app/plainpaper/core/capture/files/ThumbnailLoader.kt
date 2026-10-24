package app.plainpaper.core.capture.files

import androidx.compose.ui.graphics.ImageBitmap

/** Decodes a small image file for the UI (research R11). */
public interface ThumbnailLoader {
    public suspend fun load(
        path: String,
        maxEdge: Int = ImageFiles.THUMBNAIL_EDGE,
    ): ImageBitmap?
}
