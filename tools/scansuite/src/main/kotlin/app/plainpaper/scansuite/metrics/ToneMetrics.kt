package app.plainpaper.scansuite.metrics

/** Paper whiteness and text darkness on a rendered page (SC-002). */
object ToneMetrics {
    /** Median luminance of the page region as a fraction of white (text covers a minority of pixels). */
    fun whiteness(gray: GrayImage): Double = median(gray.histogram()) / 255.0

    /** Median luminance of ink pixels (below Otsu's threshold) as a fraction of white; null without ink. */
    fun textDarkness(gray: GrayImage): Double? {
        val hist = gray.histogram()
        val threshold = GrayImage.otsuThreshold(hist)
        val ink = IntArray(256)
        var count = 0L
        for (v in 0 until threshold) {
            ink[v] = hist[v]
            count += hist[v]
        }
        // Less than 0.2% ink means a blank page: no darkness to measure.
        if (count < gray.data.size * MIN_INK_FRACTION) return null
        return median(ink) / 255.0
    }

    /** Fraction of ink pixels (below Otsu's threshold). */
    fun inkFraction(gray: GrayImage): Double {
        val hist = gray.histogram()
        val threshold = GrayImage.otsuThreshold(hist)
        var count = 0L
        for (v in 0 until threshold) count += hist[v]
        return count.toDouble() / gray.data.size
    }

    private fun median(hist: IntArray): Int {
        val total = hist.sum().toLong()
        if (total == 0L) return 0
        var seen = 0L
        for (v in 0 until 256) {
            seen += hist[v]
            if (seen * 2 >= total) return v
        }
        return 255
    }

    private const val MIN_INK_FRACTION = 0.002
}
