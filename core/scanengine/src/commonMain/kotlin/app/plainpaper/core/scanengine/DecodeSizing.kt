package app.plainpaper.core.scanengine

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pixel size of a decoded photo. */
public data class PhotoSize(
    val width: Int,
    val height: Int,
)

/**
 * The sizes the platform [PhotoDecoder]s produce, as pure functions, so other code (the crop
 * editor's original-photo loader) can predict the pixel space the engine's corners live in.
 */
public object DecodeSizing {
    /** Android: power-of-two sample size that brings [width] x [height] to at most [maxPixels]; 1 when it already fits. */
    public fun sampleSize(
        width: Int,
        height: Int,
        maxPixels: Int,
    ): Int {
        val pixels = width.toLong() * height
        if (pixels <= maxPixels) return 1
        val needed = ceil(sqrt(pixels.toDouble() / maxPixels)).toInt()
        var sample = 1
        while (sample < needed) sample *= 2
        return sample
    }

    /** Android: the decoded size after [sampleSize] (the decoders round up). */
    public fun sampledSize(
        width: Int,
        height: Int,
        maxPixels: Int,
    ): PhotoSize {
        val sample = sampleSize(width, height, maxPixels)
        return PhotoSize((width + sample - 1) / sample, (height + sample - 1) / sample)
    }

    /** iOS: ImageIO's max pixel size, the long edge of a [maxPixels] image with a 4:3 aspect. */
    public fun thumbnailMaxEdge(maxPixels: Int): Int = ceil(sqrt(maxPixels.toDouble() * FOUR_THIRDS)).toInt()

    /** iOS: ImageIO's thumbnail size, the long edge scaled down to [maxEdge] and never up. */
    public fun fitLongEdge(
        width: Int,
        height: Int,
        maxEdge: Int,
    ): PhotoSize {
        val longEdge = maxOf(width, height)
        if (longEdge <= maxEdge) return PhotoSize(width, height)
        val scale = maxEdge.toDouble() / longEdge
        return PhotoSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    }

    private const val FOUR_THIRDS = 4.0 / 3.0
}
