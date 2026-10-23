package app.plainpaper.core.scanengine

public enum class DecodeFailure { NotFound, UnsupportedFormat, Corrupt, TooLarge }

public sealed interface DecodeResult {
    public data class Ready(
        val photo: ScanPhoto,
    ) : DecodeResult

    public data class Failed(
        val reason: DecodeFailure,
    ) : DecodeResult
}

/** Decodes JPEG/HEIC (platform permitting) honouring EXIF orientation, capped at [maxPixels] (research R12). */
public interface PhotoDecoder {
    public suspend fun decode(
        path: String,
        maxPixels: Int = DEFAULT_MAX_PIXELS,
    ): DecodeResult

    public companion object {
        public const val DEFAULT_MAX_PIXELS: Int = 12_000_000
    }
}
