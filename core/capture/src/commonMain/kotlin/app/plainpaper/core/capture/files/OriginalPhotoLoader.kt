package app.plainpaper.core.capture.files

import androidx.compose.ui.graphics.ImageBitmap
import app.plainpaper.core.scanengine.PhotoDecoder

/**
 * An original photo for the crop editor: a bitmap of at most the requested long edge, oriented
 * exactly like the scan engine's [PhotoDecoder] (EXIF applied), and the pixel size that decoder
 * produces. Page corners are stored in that size's pixel space, so the editor works in
 * [decodedWidth] x [decodedHeight] and draws [bitmap] stretched to it.
 */
public class OriginalPhoto(
    public val bitmap: ImageBitmap,
    public val decodedWidth: Int,
    public val decodedHeight: Int,
)

/** Loads originals for the crop editor (spec 004 FR-014). */
public interface OriginalPhotoLoader {
    /** The photo at [path] for [maxEdge], or null when it cannot be decoded. */
    public suspend fun load(
        path: String,
        maxEdge: Int = EDITOR_EDGE,
        maxPixels: Int = PhotoDecoder.DEFAULT_MAX_PIXELS,
    ): OriginalPhoto?

    public companion object {
        /** Long edge of the bitmap shown in the editor. */
        public const val EDITOR_EDGE: Int = 1600
    }
}
