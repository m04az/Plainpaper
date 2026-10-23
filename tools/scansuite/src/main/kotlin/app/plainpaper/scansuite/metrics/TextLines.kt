package app.plainpaper.scansuite.metrics

import kotlin.math.abs

/** Text lines found by horizontal smearing of an ink mask; each with a least-squares line fit. */
object TextLines {
    private const val MARGIN_FRACTION = 0.05

    data class Line(
        val top: Int,
        val bottom: Int,
        val left: Int,
        val width: Int,
        /** dy/dx of the linear term of the quadratic fitted to the sampled centreline. */
        val slope: Double,
        /** Largest vertical distance (px) between the centreline and its quadratic fit. */
        val maxResidual: Double,
        /** Bow: vertical deviation (px) of the quadratic term at the line ends relative to its centre. */
        val bowPx: Double,
    )

    fun find(
        ink: BooleanArray,
        width: Int,
        height: Int,
    ): List<Line> {
        val smeared = smearHorizontally(ink, width, height)
        val rowCount = IntArray(height) { y -> (0 until width).count { smeared[y * width + it] } }
        return bands(rowCount, width, height).mapNotNull { (top, bottom) -> fit(ink, width, top, bottom) }
    }

    /** Horizontal closing: a pixel is "line" if any ink lies within the kernel on its row. */
    private fun smearHorizontally(
        ink: BooleanArray,
        width: Int,
        height: Int,
    ): BooleanArray {
        val kernel = maxOf(5, width / 25)
        val smeared = BooleanArray(width * height)
        for (y in 0 until height) {
            var lastInk = -kernel
            for (x in 0 until width) {
                if (ink[y * width + x]) lastInk = x
                if (x - lastInk < kernel) smeared[y * width + x] = true
            }
            lastInk = width + kernel
            for (x in width - 1 downTo 0) {
                if (ink[y * width + x]) lastInk = x
                if (lastInk - x < kernel && lastInk < width) smeared[y * width + x] = true
            }
        }
        return smeared
    }

    /** Row runs with enough smeared pixels form line bands, split by vertical gaps. */
    private fun bands(
        rowCount: IntArray,
        width: Int,
        height: Int,
    ): List<Pair<Int, Int>> {
        val bands = mutableListOf<Pair<Int, Int>>()
        var start = -1
        // Bands inside the outer margins are page borders or desk slivers, never text.
        val margin = (height * MARGIN_FRACTION).toInt()
        for (y in 0..height) {
            val isText = y < height && rowCount[y] >= width * 0.3
            if (isText && start < 0) start = y
            if (!isText && start >= 0) {
                val inside = start >= margin && y <= height - margin
                if (inside && y - start >= 4 && y - start <= height / 10) bands += start to y
                start = -1
            }
        }
        return bands
    }

    private fun fit(
        ink: BooleanArray,
        width: Int,
        top: Int,
        bottom: Int,
    ): Line? {
        val (left, right) = inkExtent(ink, width, top, bottom) ?: return null
        val lineWidth = right - left + 1
        val xs = mutableListOf<Double>()
        val ys = mutableListOf<Double>()
        val columns = 32
        for (i in 0 until columns) {
            val x0 = left + lineWidth * i / columns
            val x1 = left + lineWidth * (i + 1) / columns
            val centre = columnCentre(ink, width, top, bottom, x0, x1) ?: continue
            xs += (x0 + x1) / 2.0
            ys += centre
        }
        if (xs.size < 6) return null
        val meanX = xs.average()
        val half = lineWidth / 2.0
        // Least squares y = a t^2 + b t + c with t = (x - meanX) / half in [-1, 1].
        val t = xs.map { (it - meanX) / half }
        val (a, b, c) = quadraticFit(t, ys)
        val maxResidual = t.indices.maxOf { i -> abs(ys[i] - (a * t[i] * t[i] + b * t[i] + c)) }
        return Line(top, bottom, left, lineWidth, slope = b / half, maxResidual = maxResidual, bowPx = abs(a))
    }

    /** Solves the 3x3 normal equations of a quadratic least-squares fit (Cramer's rule). */
    private fun quadraticFit(
        t: List<Double>,
        y: List<Double>,
    ): Triple<Double, Double, Double> {
        val s = DoubleArray(5)
        val r = DoubleArray(3)
        for (i in t.indices) {
            var power = 1.0
            for (k in 0..4) {
                s[k] += power
                if (k < 3) r[k] += y[i] * power
                power *= t[i]
            }
        }
        val m = doubleArrayOf(s[4], s[3], s[2], s[3], s[2], s[1], s[2], s[1], s[0])
        val d = det3(m)
        if (abs(d) < 1e-12) return Triple(0.0, 0.0, r[0] / s[0])
        val a = det3(doubleArrayOf(r[2], s[3], s[2], r[1], s[2], s[1], r[0], s[1], s[0])) / d
        val b = det3(doubleArrayOf(s[4], r[2], s[2], s[3], r[1], s[1], s[2], r[0], s[0])) / d
        val c = det3(doubleArrayOf(s[4], s[3], r[2], s[3], s[2], r[1], s[2], s[1], r[0])) / d
        return Triple(a, b, c)
    }

    private fun det3(m: DoubleArray): Double =
        m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])

    private fun inkExtent(
        ink: BooleanArray,
        width: Int,
        top: Int,
        bottom: Int,
    ): Pair<Int, Int>? {
        var left = width
        var right = -1
        for (x in 0 until width) {
            val hasInk = (top until bottom).any { y -> ink[y * width + x] }
            if (hasInk) {
                left = minOf(left, x)
                right = maxOf(right, x)
            }
        }
        return if (right > left) left to right else null
    }

    private fun columnCentre(
        ink: BooleanArray,
        width: Int,
        top: Int,
        bottom: Int,
        x0: Int,
        x1: Int,
    ): Double? {
        var sumY = 0.0
        var count = 0
        for (y in top until bottom) {
            for (x in x0 until x1) {
                if (ink[y * width + x]) {
                    sumY += y
                    count++
                }
            }
        }
        return if (count > 0) sumY / count else null
    }
}
