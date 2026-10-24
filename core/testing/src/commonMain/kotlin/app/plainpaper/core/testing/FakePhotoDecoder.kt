package app.plainpaper.core.testing

import app.plainpaper.core.scanengine.DecodeFailure
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.PhotoDecoder
import kotlinx.coroutines.CompletableDeferred

/** Paths containing "unreadable" fail to decode; [block] holds every decode until [release]. */
class FakePhotoDecoder : PhotoDecoder {
    val decoded = mutableListOf<String>()
    var block: Boolean = false
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    override suspend fun decode(
        path: String,
        maxPixels: Int,
    ): DecodeResult {
        decoded += path
        if (block) gates.getOrPut(path) { CompletableDeferred() }.await()
        return if (path.contains("unreadable")) {
            DecodeResult.Failed(DecodeFailure.Corrupt)
        } else {
            DecodeResult.Ready(FakeScanPhoto(PHOTO_WIDTH, PHOTO_HEIGHT))
        }
    }

    val blocked: Set<String> get() = gates.filterValues { !it.isCompleted }.keys

    fun release(path: String) {
        gates.getOrPut(path) { CompletableDeferred() }.complete(Unit)
    }

    fun releaseAll() {
        gates.keys.toList().forEach(::release)
    }

    private companion object {
        const val PHOTO_WIDTH = 4000
        const val PHOTO_HEIGHT = 3000
    }
}
