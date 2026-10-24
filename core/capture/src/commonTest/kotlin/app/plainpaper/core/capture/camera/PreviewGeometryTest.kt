package app.plainpaper.core.capture.camera

import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewGeometryTest {
    private fun assertPoint(
        expected: Point,
        actual: Point,
        tolerance: Float = 0.5f,
    ) {
        assertTrue(
            abs(expected.x - actual.x) <= tolerance && abs(expected.y - actual.y) <= tolerance,
            "expected $expected, got $actual",
        )
    }

    @Test
    fun landscapeFrameFillsAPortraitViewByHeightAndCentresHorizontally() {
        // No rotation: 640x480 into 390x844 → scale by height (844/480 = 1.758), 1125 px wide, cropped.
        val g = PreviewGeometry(640, 480, 0, 390, 844)
        assertEquals(844f / 480f, g.scale, 1e-4f)
        assertPoint(Point(-367.5f, 0f), g.frameToView(Point(0f, 0f)))
        assertPoint(Point(195f, 422f), g.frameToView(Point(320f, 240f)))
    }

    @Test
    fun ninetyDegreeRotationMapsTopLeftToTopRight() {
        // Sensor landscape frame rotated 90° clockwise becomes 480x640 upright.
        val g = PreviewGeometry(640, 480, 90, 390, 844)
        assertEquals(844f / 640f, g.scale, 1e-4f)
        val topLeft = g.frameToView(Point(0f, 0f))
        val bottomLeft = g.frameToView(Point(0f, 480f))
        assertTrue(topLeft.x > 390f / 2 && topLeft.y < 1f, "frame TL should land top-right: $topLeft")
        assertPoint(Point((0 - (480 * g.scale - 390) / 2), 0f), bottomLeft)
        assertPoint(Point(195f, 422f), g.frameToView(Point(320f, 240f)))
    }

    @Test
    fun halfAndThreeQuarterTurns() {
        val g180 = PreviewGeometry(640, 480, 180, 640, 480)
        assertPoint(Point(640f, 480f), g180.frameToView(Point(0f, 0f)))
        val g270 = PreviewGeometry(640, 480, 270, 480, 640)
        assertPoint(Point(0f, 640f), g270.frameToView(Point(0f, 0f)))
        assertPoint(Point(480f, 0f), g270.frameToView(Point(640f, 480f)))
    }

    @Test
    fun quadKeepsCornerOrderAndDiagonalIsReported() {
        val g = PreviewGeometry(640, 480, 0, 640, 480)
        val quad = Quad(Point(10f, 10f), Point(600f, 20f), Point(590f, 470f), Point(20f, 460f))
        assertEquals(quad, g.frameToView(quad))
        assertEquals(800f, g.frameDiagonal, 1e-3f)
    }
}
