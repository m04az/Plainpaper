package app.plainpaper.core.domain.model

import kotlin.math.roundToInt

/** Output pixel dimensions of a rendered page. */
public data class PixelSize(
    val width: Int,
    val height: Int,
)

/**
 * Physical page size of a rendered scan at 300 DPI (spec 002, FR-008).
 *
 * `A4` and `Letter` have fixed pixel sizes; `Natural` keeps the detected page proportions with
 * the longer side equal to an A4 long side so receipts become tall narrow images.
 */
public enum class PageSize {
    A4,
    Letter,
    Natural,
    ;

    /**
     * Pixel size of the output image.
     *
     * @param detectedAspect width / height of the corrected page; only used by [Natural],
     *   clamped to [NATURAL_ASPECT_MIN]..[NATURAL_ASPECT_MAX].
     * @param landscape swaps width and height for [A4] and [Letter]; ignored by [Natural],
     *   whose orientation follows the aspect ratio.
     */
    public fun outputPixels(
        detectedAspect: Double,
        landscape: Boolean,
    ): PixelSize =
        when (this) {
            A4 -> A4_PORTRAIT.oriented(landscape)
            Letter -> LETTER_PORTRAIT.oriented(landscape)
            Natural -> naturalPixels(detectedAspect)
        }

    public companion object {
        /** A4 at 300 DPI, portrait. */
        public val A4_PORTRAIT: PixelSize = PixelSize(width = 2480, height = 3508)

        /** US Letter at 300 DPI, portrait. */
        public val LETTER_PORTRAIT: PixelSize = PixelSize(width = 2550, height = 3300)

        /** Long side of a [Natural] page: the A4 long side at 300 DPI. */
        public const val NATURAL_LONG_EDGE: Int = 3508
        public const val NATURAL_ASPECT_MIN: Double = 0.2
        public const val NATURAL_ASPECT_MAX: Double = 5.0

        private val LETTER_REGIONS = setOf("US", "CA", "MX", "PH")

        /** Regional default: Letter in the US, Canada, Mexico and the Philippines, A4 elsewhere. */
        public fun defaultFor(regionCode: String?): PageSize =
            if (regionCode?.trim()?.uppercase() in LETTER_REGIONS) Letter else A4

        private fun PixelSize.oriented(landscape: Boolean): PixelSize =
            if (landscape) PixelSize(width = height, height = width) else this

        private fun naturalPixels(detectedAspect: Double): PixelSize {
            val aspect = detectedAspect.coerceIn(NATURAL_ASPECT_MIN, NATURAL_ASPECT_MAX)
            return if (aspect >= 1.0) {
                PixelSize(width = NATURAL_LONG_EDGE, height = (NATURAL_LONG_EDGE / aspect).toEven())
            } else {
                PixelSize(width = (NATURAL_LONG_EDGE * aspect).toEven(), height = NATURAL_LONG_EDGE)
            }
        }

        private fun Double.toEven(): Int {
            val rounded = roundToInt()
            return if (rounded % 2 == 0) rounded else rounded + 1
        }
    }
}
