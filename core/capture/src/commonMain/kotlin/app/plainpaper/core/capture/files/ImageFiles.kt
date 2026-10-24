package app.plainpaper.core.capture.files

import app.plainpaper.core.scanengine.ScanOutput

public enum class CopyFailure { NotFound, NotAnImage, Io }

public sealed interface CopyResult {
    public data class Copied(
        val path: String,
        val bytes: Long,
    ) : CopyResult

    public data class Failed(
        val reason: CopyFailure,
    ) : CopyResult
}

/** Image file utilities the capture flows need (research R11); thumbnails are loaded by [ThumbnailLoader]. */
public interface ImageFiles {
    /** Encodes a rendered page as JPEG; false when the output type or the write fails. */
    public suspend fun writeJpeg(
        output: ScanOutput,
        path: String,
        quality: Int = DEFAULT_QUALITY,
    ): Boolean

    /** Downscaled JPEG copy of an image file. */
    public suspend fun writeThumbnail(
        fromPath: String,
        toPath: String,
        maxEdge: Int = THUMBNAIL_EDGE,
    ): Boolean

    /** Copies a picked photo into the session folder, verifying it decodes as an image. */
    public suspend fun copyOriginal(
        uri: String,
        destinationPath: String,
    ): CopyResult

    /** True when a non-empty file exists at [path]. */
    public fun exists(path: String): Boolean

    /** Free bytes on the volume that holds [directory]; `Long.MAX_VALUE` when unknown. */
    public fun freeBytes(directory: String): Long

    public companion object {
        public const val DEFAULT_QUALITY: Int = 90
        public const val THUMBNAIL_EDGE: Int = 400

        /** Capture refuses new pages below this much free space (spec edge case). */
        public const val MIN_FREE_BYTES: Long = 50L * 1024 * 1024
    }
}
