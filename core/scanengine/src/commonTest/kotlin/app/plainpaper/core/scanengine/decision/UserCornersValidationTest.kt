package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.scanengine.model.Point
import app.plainpaper.core.scanengine.model.Quad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserCornersValidationTest {
    private val width = 4000
    private val height = 3000

    @Test
    fun validCornersPass() {
        val quad = Quad(Point(100f, 100f), Point(3900f, 120f), Point(3880f, 2900f), Point(120f, 2880f))
        assertNull(UserCornersValidation.validate(quad, width, height))
    }

    @Test
    fun slightlyOutsideWithinToleranceIsAccepted() {
        val quad = Quad(Point(-70f, -50f), Point(4070f, -50f), Point(4070f, 3050f), Point(-70f, 3050f))
        assertNull(UserCornersValidation.validate(quad, width, height))
    }

    @Test
    fun farOutsideIsRejected() {
        val quad = Quad(Point(-500f, 0f), Point(4000f, 0f), Point(4000f, 3000f), Point(0f, 3000f))
        assertEquals("corners", UserCornersValidation.validate(quad, width, height))
    }

    @Test
    fun nonConvexIsRejected() {
        val bowTie = Quad(Point(0f, 0f), Point(4000f, 3000f), Point(4000f, 0f), Point(0f, 3000f))
        assertEquals("corners", UserCornersValidation.validate(bowTie, width, height))
    }

    @Test
    fun tinyAreaIsRejected() {
        val tiny = Quad(Point(0f, 0f), Point(400f, 0f), Point(400f, 300f), Point(0f, 300f)) // 1%
        assertEquals("corners", UserCornersValidation.validate(tiny, width, height))
    }
}
