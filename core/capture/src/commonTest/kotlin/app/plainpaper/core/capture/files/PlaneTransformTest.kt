package app.plainpaper.core.capture.files

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PlaneTransformTest {
    // 3 wide, 2 high:  1 2 3 / 4 5 6 (stride 4, padding 9)
    private val src = byteArrayOf(1, 2, 3, 9, 4, 5, 6, 9)

    private fun run(
        rotation: Int,
        mirrored: Boolean,
    ) = transformPlane(src, 4, 3, 2, rotation, mirrored)

    @Test
    fun identityDropsPadding() {
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), run(0, false).data)
    }

    @Test
    fun clockwiseQuarterTurn() {
        val plane = run(90, false)
        assertEquals(2 to 3, plane.width to plane.height)
        assertContentEquals(byteArrayOf(4, 1, 5, 2, 6, 3), plane.data)
    }

    @Test
    fun halfAndThreeQuarterTurns() {
        assertContentEquals(byteArrayOf(6, 5, 4, 3, 2, 1), run(180, false).data)
        assertContentEquals(byteArrayOf(3, 6, 2, 5, 1, 4), run(270, false).data)
    }

    @Test
    fun mirrorFlipsLeftToRightBeforeRotating() {
        assertContentEquals(byteArrayOf(3, 2, 1, 6, 5, 4), run(0, true).data)
        // mirrored = 3 2 1 / 6 5 4; clockwise quarter turn = 6 3 / 5 2 / 4 1
        assertContentEquals(byteArrayOf(6, 3, 5, 2, 4, 1), run(90, true).data)
    }
}
