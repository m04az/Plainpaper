package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.sqrt

/** `javax.imageio` decoder (JPEG/PNG) for the host suite; the reference set is stored EXIF-baked. */
public class ImageIoPhotoDecoder(
    private val dispatchers: DispatcherProvider,
) : PhotoDecoder {
    override suspend fun decode(
        path: String,
        maxPixels: Int,
    ): DecodeResult =
        withContext(dispatchers.io) {
            val file = File(path)
            if (!file.isFile) return@withContext DecodeResult.Failed(DecodeFailure.NotFound)
            val stream =
                ImageIO.createImageInputStream(file) ?: return@withContext DecodeResult.Failed(DecodeFailure.Corrupt)
            stream.use { input ->
                val reader =
                    ImageIO.getImageReaders(input).asSequence().firstOrNull()
                        ?: return@withContext DecodeResult.Failed(DecodeFailure.UnsupportedFormat)
                try {
                    reader.input = input
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    val pixels = width.toLong() * height
                    val params = reader.defaultReadParam
                    if (pixels > maxPixels) {
                        val step = ceil(sqrt(pixels.toDouble() / maxPixels)).toInt()
                        params.setSourceSubsampling(step, step, 0, 0)
                    }
                    val decoded = reader.read(0, params)
                    DecodeResult.Ready(BufferedImageScanPhoto(decoded.asIntArgb()))
                } catch (_: java.io.IOException) {
                    DecodeResult.Failed(DecodeFailure.Corrupt)
                } finally {
                    reader.dispose()
                }
            }
        }

    private fun BufferedImage.asIntArgb(): BufferedImage {
        if (type == BufferedImage.TYPE_INT_ARGB) return this
        val converted = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = converted.createGraphics()
        graphics.drawImage(this, 0, 0, null)
        graphics.dispose()
        return converted
    }
}
