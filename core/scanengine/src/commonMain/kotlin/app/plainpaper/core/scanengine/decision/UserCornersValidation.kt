package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.scanengine.model.Quad

/** Validates caller-supplied corners before any native call (data-model.md, FR-003). */
public object UserCornersValidation {
    public const val BOUNDS_TOLERANCE: Float = 0.02f
    public const val MIN_AREA_FRACTION: Double = 0.05

    /** Returns null when valid, otherwise the `InvalidInput` detail. */
    public fun validate(
        corners: Quad,
        photoWidth: Int,
        photoHeight: Int,
    ): String? {
        val tolX = photoWidth * BOUNDS_TOLERANCE
        val tolY = photoHeight * BOUNDS_TOLERANCE
        val outside =
            corners.points.any { p ->
                p.x < -tolX || p.y < -tolY || p.x > photoWidth + tolX || p.y > photoHeight + tolY
            }
        return when {
            outside -> "corners"
            !corners.isConvex -> "corners"
            corners.area < MIN_AREA_FRACTION * photoWidth * photoHeight -> "corners"
            else -> null
        }
    }
}
