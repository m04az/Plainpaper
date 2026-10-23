package app.plainpaper.scansuite.metrics

import kotlin.math.abs
import kotlin.math.tan

/** Text-line skew of a rendered page in degrees (SC-004 proxy and the deskew check). */
object Straightness {
    /**
     * Angle (degrees, positive = clockwise) that maximises the variance of the ink projection
     * profile over -[maxDegrees]..+[maxDegrees]; 0 when the page has no ink.
     */
    fun skewDegrees(
        gray: GrayImage,
        maxDegrees: Double = 3.0,
        stepDegrees: Double = 0.1,
    ): Double {
        val small = gray.downscaled(800)
        val threshold = GrayImage.otsuThreshold(small.histogram())
        val ink = BooleanArray(small.width * small.height) { small.data[it].toInt() and 0xFF < threshold }
        if (ink.count { it } < small.width * small.height * 0.001) return 0.0
        val cx = small.width / 2.0
        var bestAngle = 0.0
        var bestVariance = -1.0
        var angle = -maxDegrees
        while (angle <= maxDegrees + 1e-9) {
            val variance = profileVariance(ink, small.width, small.height, tan(Math.toRadians(angle)), cx)
            if (variance > bestVariance) {
                bestVariance = variance
                bestAngle = angle
            }
            angle += stepDegrees
        }
        return if (abs(bestAngle) < 1e-9) 0.0 else bestAngle
    }

    private fun profileVariance(
        ink: BooleanArray,
        width: Int,
        height: Int,
        slope: Double,
        cx: Double,
    ): Double {
        val profile = DoubleArray(height)
        for (y in 0 until height) {
            var sum = 0
            for (x in 0 until width) {
                val yy = (y + (x - cx) * slope).toInt()
                if (yy in 0 until height && ink[yy * width + x]) sum++
            }
            profile[y] = sum.toDouble()
        }
        val mean = profile.average()
        return profile.sumOf { (it - mean) * (it - mean) } / height
    }
}
