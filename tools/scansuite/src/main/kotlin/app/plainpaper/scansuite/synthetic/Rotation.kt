package app.plainpaper.scansuite.synthetic

import java.awt.image.BufferedImage

/** Clockwise image and point rotation by multiples of 90 degrees. */
object Rotation {
    fun rotate(
        image: BufferedImage,
        degrees: Int,
    ): BufferedImage {
        if (degrees == 0) return image
        val w = image.width
        val h = image.height
        val out = if (degrees == 180) BufferedImage(w, h, image.type) else BufferedImage(h, w, image.type)
        val pixels = IntArray(w * h)
        image.getRGB(0, 0, w, h, pixels, 0, w)
        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val (nx, ny) = rotateIndex(x, y, degrees, w, h)
                outPixels[ny * out.width + nx] = pixels[y * w + x]
            }
        }
        out.setRGB(0, 0, out.width, out.height, outPixels, 0, out.width)
        return out
    }

    private fun rotateIndex(
        x: Int,
        y: Int,
        degrees: Int,
        w: Int,
        h: Int,
    ): Pair<Int, Int> =
        when (degrees) {
            90 -> Pair(h - 1 - y, x)
            180 -> Pair(w - 1 - x, h - 1 - y)
            270 -> Pair(y, w - 1 - x)
            else -> Pair(x, y)
        }

    fun rotatePoint(
        point: Pair<Double, Double>,
        degrees: Int,
        w: Int,
        h: Int,
    ): Pair<Double, Double> =
        when (degrees) {
            90 -> Pair(h - point.second, point.first)
            180 -> Pair(w - point.first, h - point.second)
            270 -> Pair(point.second, w - point.first)
            else -> point
        }
}
