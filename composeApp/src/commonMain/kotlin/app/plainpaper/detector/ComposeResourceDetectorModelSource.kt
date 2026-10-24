package app.plainpaper.detector

import app.plainpaper.composeapp.resources.Res
import app.plainpaper.core.scanengine.DetectorModelSource
import kotlinx.coroutines.CancellationException

/**
 * Reads the bundled detection model (`composeResources/files/detector.ppdm`). Returns null when the
 * resource is missing or unreadable, so the engine keeps the classic detector.
 */
class ComposeResourceDetectorModelSource(
    private val read: suspend (path: String) -> ByteArray = { path -> Res.readBytes(path) },
) : DetectorModelSource {
    override suspend fun bytes(): ByteArray? =
        try {
            read(MODEL_PATH)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") _: Exception,
        ) {
            null
        }

    companion object {
        const val MODEL_PATH: String = "files/detector.ppdm"
    }
}
