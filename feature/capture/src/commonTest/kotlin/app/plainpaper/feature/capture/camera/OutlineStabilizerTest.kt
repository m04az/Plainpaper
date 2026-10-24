package app.plainpaper.feature.capture.camera

import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutlineStabilizerTest {
    private val page = Quad(Point(100f, 60f), Point(540f, 60f), Point(540f, 420f), Point(100f, 420f))
    private val elsewhere = Quad(Point(10f, 10f), Point(200f, 10f), Point(200f, 150f), Point(10f, 150f))

    private fun Quad.shifted(dx: Float) =
        Quad(
            Point(topLeft.x + dx, topLeft.y),
            Point(topRight.x + dx, topRight.y),
            Point(bottomRight.x + dx, bottomRight.y),
            Point(bottomLeft.x + dx, bottomLeft.y),
        )

    @Test
    fun aSingleDetectionDoesNotShowAnOutline() {
        val s = OutlineStabilizer(frameDiagonal = 800f)
        assertNull(s.onFrame(0, page))
        assertNotNull(s.onFrame(33, page))
    }

    @Test
    fun cornersAreSmoothedWhileTheSamePageMoves() {
        val s = OutlineStabilizer(frameDiagonal = 800f)
        s.onFrame(0, page)
        s.onFrame(33, page)
        val moved = assertNotNull(s.onFrame(66, page.shifted(40f)))
        assertEquals(120f, moved.topLeft.x, 0.01f) // half way from 100 to 140
    }

    @Test
    fun shortMissesKeepTheOutlineThenItDisappears() {
        val s = OutlineStabilizer(frameDiagonal = 800f)
        s.onFrame(0, page)
        s.onFrame(33, page)
        assertNotNull(s.onFrame(200, null))
        assertNotNull(s.onFrame(433, null))
        assertNull(s.onFrame(500, null))
    }

    @Test
    fun aOneFrameJumpIsIgnoredButARepeatedOneReplacesTheOutline() {
        val s = OutlineStabilizer(frameDiagonal = 800f)
        s.onFrame(0, page)
        s.onFrame(33, page)
        assertEquals(page.topLeft, s.onFrame(66, elsewhere)?.topLeft) // spurious frame ignored
        assertEquals(page.topLeft, s.onFrame(99, page)?.topLeft)
        s.onFrame(132, elsewhere)
        val switched = assertNotNull(s.onFrame(165, elsewhere))
        assertTrue(switched.topLeft.x < 50f)
    }

    @Test
    fun alternatingNoiseNeverShowsAnOutline() {
        val s = OutlineStabilizer(frameDiagonal = 800f)
        val frames = (0 until 20).map { i -> s.onFrame(i * 33L, if (i % 2 == 0) page else elsewhere) }
        assertTrue(frames.all { it == null })
    }
}
