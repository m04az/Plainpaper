package app.plainpaper.scansuite.metrics

import java.awt.image.BufferedImage

/** 8-bit grey copy of an image region used by the metrics. */
class GrayImage(
    val width: Int,
    val height: Int,
    val data: ByteArray,
) {
    operator fun get(
        x: Int,
        y: Int,
    ): Int = data[y * width + x].toInt() and 0xFF

    fun histogram(): IntArray {
        val hist = IntArray(256)
        for (b in data) hist[b.toInt() and 0xFF]++
        return hist
    }

    /** Nearest-neighbour downscale so the long edge is at most [maxLongEdge]. */
    fun downscaled(maxLongEdge: Int): GrayImage {
        val longEdge = maxOf(width, height)
        if (longEdge <= maxLongEdge) return this
        val scale = maxLongEdge.toDouble() / longEdge
        val w = maxOf(1, (width * scale).toInt())
        val h = maxOf(1, (height * scale).toInt())
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            val sy = minOf(height - 1, (y / scale).toInt())
            for (x in 0 until w) {
                val sx = minOf(width - 1, (x / scale).toInt())
                out[y * w + x] = data[sy * width + sx]
            }
        }
        return GrayImage(w, h, out)
    }

    companion object {
        fun of(
            image: BufferedImage,
            x0: Int = 0,
            y0: Int = 0,
            width: Int = image.width,
            height: Int = image.height,
        ): GrayImage {
            val data = ByteArray(width * height)
            if (image.type == BufferedImage.TYPE_BYTE_GRAY) {
                val raster = image.raster
                val row = IntArray(width)
                for (y in 0 until height) {
                    raster.getSamples(x0, y0 + y, width, 1, 0, row)
                    for (x in 0 until width) data[y * width + x] = row[x].toByte()
                }
            } else {
                val row = IntArray(width)
                for (y in 0 until height) {
                    image.getRGB(x0, y0 + y, width, 1, row, 0, width)
                    for (x in 0 until width) {
                        val argb = row[x]
                        val r = (argb shr 16) and 0xFF
                        val g = (argb shr 8) and 0xFF
                        val b = argb and 0xFF
                        data[y * width + x] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
                    }
                }
            }
            return GrayImage(width, height, data)
        }

        /** Otsu's threshold on a histogram; ink is strictly below it. */
        fun otsuThreshold(hist: IntArray): Int {
            val total = hist.sum().toDouble()
            if (total == 0.0) return 128
            var sum = 0.0
            for (i in 0 until 256) sum += i * hist[i]
            var sumB = 0.0
            var wB = 0.0
            var best = 0.0
            var threshold = 128
            var t = 0
            while (t < 256) {
                wB += hist[t]
                val wF = total - wB
                if (wF == 0.0) break
                sumB += t * hist[t]
                if (wB > 0.0) {
                    val mB = sumB / wB
                    val mF = (sum - sumB) / wF
                    val between = wB * wF * (mB - mF) * (mB - mF)
                    if (between > best) {
                        best = between
                        threshold = t + 1
                    }
                }
                t++
            }
            return threshold
        }
    }
}
