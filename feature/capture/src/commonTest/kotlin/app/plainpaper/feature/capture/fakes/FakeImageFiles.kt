package app.plainpaper.feature.capture.fakes

import app.plainpaper.core.capture.files.CopyFailure
import app.plainpaper.core.capture.files.CopyResult
import app.plainpaper.core.capture.files.ImageFiles
import app.plainpaper.core.scanengine.ScanOutput

/** Records written paths; "unreadable" URIs fail to copy, "missing" URIs are not found. */
class FakeImageFiles : ImageFiles {
    val jpegs = mutableListOf<String>()
    val thumbnails = mutableListOf<Pair<String, String>>()
    val copies = mutableListOf<Pair<String, String>>()
    var freeBytes: Long = Long.MAX_VALUE
    var failWrites: Boolean = false

    override suspend fun writeJpeg(
        output: ScanOutput,
        path: String,
        quality: Int,
    ): Boolean {
        if (failWrites) return false
        jpegs += path
        return true
    }

    override suspend fun writeThumbnail(
        fromPath: String,
        toPath: String,
        maxEdge: Int,
    ): Boolean {
        if (failWrites) return false
        thumbnails += fromPath to toPath
        return true
    }

    override suspend fun copyOriginal(
        uri: String,
        destinationPath: String,
    ): CopyResult {
        copies += uri to destinationPath
        return when {
            uri.contains("missing") -> CopyResult.Failed(CopyFailure.NotFound)
            uri.contains("unreadable") -> CopyResult.Failed(CopyFailure.NotAnImage)
            else -> CopyResult.Copied(destinationPath, 1_000L)
        }
    }

    override fun freeBytes(directory: String): Long = freeBytes
}
