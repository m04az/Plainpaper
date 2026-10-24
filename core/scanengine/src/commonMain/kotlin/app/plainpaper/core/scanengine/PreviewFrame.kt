package app.plainpaper.core.scanengine

/** Byte order of a preview frame's 4-byte pixels. */
public enum class PixelFormat {
    RGBA,
    BGRA,
    ;

    /** `pp_detect` flags (`PP_FRAME_BGRA`). */
    public val nativeFlags: Int get() = if (this == BGRA) 1 else 0
}

/**
 * A small camera preview frame handed to [ScanEngine.detect] (specs/003 data-model.md). Pixels
 * are owned by the camera stack and read in place; a frame is only valid until the camera
 * recycles it, so callers never keep one past the detect call.
 */
public interface PreviewFrame {
    public val width: Int
    public val height: Int
    public val strideBytes: Int
    public val format: PixelFormat
    public val timestampMillis: Long
}

/** A frame whose pixels live in a Kotlin byte array (host suite, iOS copies, tests). */
public class ByteArrayPreviewFrame(
    override val width: Int,
    override val height: Int,
    override val strideBytes: Int,
    override val format: PixelFormat,
    override val timestampMillis: Long,
    public val pixels: ByteArray,
) : PreviewFrame {
    init {
        require(width > 0 && height > 0) { "empty frame" }
        require(strideBytes >= width * BYTES_PER_PIXEL) { "stride smaller than width" }
        require(pixels.size >= strideBytes * height) { "pixel buffer too small" }
    }

    public companion object {
        public const val BYTES_PER_PIXEL: Int = 4
    }
}
