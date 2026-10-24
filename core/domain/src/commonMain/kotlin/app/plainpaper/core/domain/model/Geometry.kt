package app.plainpaper.core.domain.model

import kotlin.math.abs

/** A point in input-photo pixels (origin top-left, x right, y down). */
public data class Point(
    val x: Float,
    val y: Float,
)

/** Four page corners in input-photo pixels, clockwise from the top-left. */
public data class Quad(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point,
) {
    public val points: List<Point>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Shoelace area in square pixels. */
    public val area: Double
        get() {
            var sum = 0.0
            val pts = points
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                sum += a.x.toDouble() * b.y - b.x.toDouble() * a.y
            }
            return abs(sum) / 2.0
        }

    /** True when every turn has the same sign (no self-intersection, no collinear triple). */
    public val isConvex: Boolean
        get() {
            val pts = points
            var sign = 0
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                val c = pts[(i + 2) % pts.size]
                val cross = (b.x - a.x).toDouble() * (c.y - b.y) - (b.y - a.y).toDouble() * (c.x - b.x)
                val s =
                    if (cross > 0) {
                        1
                    } else if (cross < 0) {
                        -1
                    } else {
                        0
                    }
                if (s == 0) return false
                if (sign == 0) {
                    sign = s
                } else if (s != sign) {
                    return false
                }
            }
            return true
        }

    /** `[x0, y0, x1, y1, x2, y2, x3, y3]` in TL, TR, BR, BL order (bridge wire format). */
    public fun toFloatArray(): FloatArray =
        floatArrayOf(
            topLeft.x,
            topLeft.y,
            topRight.x,
            topRight.y,
            bottomRight.x,
            bottomRight.y,
            bottomLeft.x,
            bottomLeft.y,
        )

    public companion object {
        public fun fullFrame(
            width: Int,
            height: Int,
        ): Quad {
            val w = width.toFloat()
            val h = height.toFloat()
            return Quad(Point(0f, 0f), Point(w, 0f), Point(w, h), Point(0f, h))
        }

        @Suppress("MagicNumber") // wire format: 8 coordinates in TL, TR, BR, BL order
        public fun fromDoubles(values: DoubleArray): Quad =
            Quad(
                Point(values[0].toFloat(), values[1].toFloat()),
                Point(values[2].toFloat(), values[3].toFloat()),
                Point(values[4].toFloat(), values[5].toFloat()),
                Point(values[6].toFloat(), values[7].toFloat()),
            )
    }
}

/** Page-detection confidence (spec 002 FR-002). */
public enum class Confidence { High, Low, NotFound }
