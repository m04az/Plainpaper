package app.plainpaper.scansuite.synthetic

import kotlin.math.abs

/** 3x3 projective transform stored row-major. */
class Homography(
    val m: DoubleArray,
) {
    fun apply(
        x: Double,
        y: Double,
    ): Pair<Double, Double> {
        val w = m[6] * x + m[7] * y + m[8]
        return Pair((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w)
    }

    fun inverse(): Homography {
        val a = m
        val det =
            a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6])
        require(abs(det) > 1e-12) { "singular homography" }
        val inv =
            doubleArrayOf(
                (a[4] * a[8] - a[5] * a[7]) / det,
                (a[2] * a[7] - a[1] * a[8]) / det,
                (a[1] * a[5] - a[2] * a[4]) / det,
                (a[5] * a[6] - a[3] * a[8]) / det,
                (a[0] * a[8] - a[2] * a[6]) / det,
                (a[2] * a[3] - a[0] * a[5]) / det,
                (a[3] * a[7] - a[4] * a[6]) / det,
                (a[1] * a[6] - a[0] * a[7]) / det,
                (a[0] * a[4] - a[1] * a[3]) / det,
            )
        return Homography(inv)
    }

    companion object {
        private fun swapPivotRow(
            a: Array<DoubleArray>,
            col: Int,
        ) {
            var pivot = col
            for (row in col + 1 until 8) if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
            val tmp = a[col]
            a[col] = a[pivot]
            a[pivot] = tmp
        }

        private fun eliminate(
            a: Array<DoubleArray>,
            row: Int,
            col: Int,
        ) {
            val f = if (row == col) 0.0 else a[row][col]
            if (f == 0.0) return
            for (k in col until 9) a[row][k] -= f * a[col][k]
        }

        /** Maps the four `src` points onto the four `dst` points (direct linear transform, 8 unknowns). */
        fun from(
            src: List<Pair<Double, Double>>,
            dst: List<Pair<Double, Double>>,
        ): Homography {
            require(src.size == 4 && dst.size == 4)
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val (x, y) = src[i]
                val (u, v) = dst[i]
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
            }
            // Gaussian elimination with partial pivoting on the augmented 8x9 matrix.
            for (col in 0 until 8) {
                swapPivotRow(a, col)
                val p = a[col][col]
                require(abs(p) > 1e-12) { "degenerate point configuration" }
                for (k in col until 9) a[col][k] /= p
                for (row in 0 until 8) eliminate(a, row, col)
            }
            val h = DoubleArray(9) { if (it < 8) a[it][8] else 1.0 }
            return Homography(h)
        }
    }
}
