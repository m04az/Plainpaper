package app.plainpaper.core.capture.files

/** A tightly packed 8-bit plane. */
internal class Plane(
    val data: ByteArray,
    val width: Int,
    val height: Int,
)

private const val QUARTER = 90
private const val HALF = 180
private const val THREE_QUARTERS = 270
private const val FULL = 360

/**
 * Mirrors [src] left to right when [mirrored], then rotates it clockwise by [rotation]
 * (0/90/180/270). Rows of [src] are [srcStride] bytes apart.
 */
internal fun transformPlane(
    src: ByteArray,
    srcStride: Int,
    width: Int,
    height: Int,
    rotation: Int,
    mirrored: Boolean,
): Plane {
    val turn = ((rotation % FULL) + FULL) % FULL
    val swap = turn == QUARTER || turn == THREE_QUARTERS
    val outWidth = if (swap) height else width
    val outHeight = if (swap) width else height
    val out = ByteArray(outWidth * outHeight)
    for (dy in 0 until outHeight) {
        for (dx in 0 until outWidth) {
            // Source pixel of the mirrored image that lands on (dx, dy) after the clockwise turn.
            var sx: Int
            val sy: Int
            when (turn) {
                QUARTER -> {
                    sx = dy
                    sy = height - 1 - dx
                }

                HALF -> {
                    sx = width - 1 - dx
                    sy = height - 1 - dy
                }

                THREE_QUARTERS -> {
                    sx = width - 1 - dy
                    sy = dx
                }

                else -> {
                    sx = dx
                    sy = dy
                }
            }
            if (mirrored) sx = width - 1 - sx
            out[dy * outWidth + dx] = src[sy * srcStride + sx]
        }
    }
    return Plane(out, outWidth, outHeight)
}
