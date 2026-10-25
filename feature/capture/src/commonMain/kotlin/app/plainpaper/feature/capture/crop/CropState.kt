package app.plainpaper.feature.capture.crop

import app.plainpaper.core.capture.files.OriginalPhoto
import app.plainpaper.core.capture.files.OriginalPhotoLoader
import app.plainpaper.core.domain.model.Quad
import app.plainpaper.core.domain.model.capture.CapturedPage

/**
 * The crop editor of one page (spec 004 FR-014). While [photo] is null the original is still
 * loading (Apply stays disabled); [failed] is set when it could not be opened. [corners] and
 * [detected] are in the pixel space of [photo]'s decoded size.
 */
data class CropState(
    val pageId: String,
    val photo: OriginalPhoto? = null,
    val corners: Quad? = null,
    val detected: Quad? = null,
    val failed: Boolean = false,
) {
    val loading: Boolean get() = photo == null && !failed
}

/** Loads the original of [page] and its current border; the full frame when the page has none. */
suspend fun OriginalPhotoLoader.cropStateFor(page: CapturedPage): CropState {
    val photo = load(page.originalPath) ?: return CropState(page.id, failed = true)
    return CropState(
        pageId = page.id,
        photo = photo,
        corners = page.corners ?: Quad.fullFrame(photo.decodedWidth, photo.decodedHeight),
        detected = page.detectedCorners,
    )
}
