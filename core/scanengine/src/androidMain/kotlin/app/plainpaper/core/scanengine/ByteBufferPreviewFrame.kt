package app.plainpaper.core.scanengine

import java.nio.ByteBuffer

/** A camera analysis plane in a direct [ByteBuffer], read in place by the native code. */
public class ByteBufferPreviewFrame(
    override val width: Int,
    override val height: Int,
    override val strideBytes: Int,
    override val format: PixelFormat,
    override val timestampMillis: Long,
    public val buffer: ByteBuffer,
) : PreviewFrame {
    init {
        require(buffer.isDirect) { "preview frame buffers must be direct" }
        require(strideBytes >= width * ByteArrayPreviewFrame.BYTES_PER_PIXEL) { "stride smaller than width" }
        require(buffer.capacity() >= strideBytes * height) { "pixel buffer too small" }
    }
}
