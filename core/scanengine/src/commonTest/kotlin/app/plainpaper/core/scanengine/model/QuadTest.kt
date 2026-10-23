package app.plainpaper.core.scanengine.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuadTest {
    private val rect = Quad(Point(0f, 0f), Point(100f, 0f), Point(100f, 50f), Point(0f, 50f))

    @Test
    fun areaIsShoelaceAndOrderIndependent() {
        assertEquals(5000.0, rect.area, 1e-9)
        val reversed = Quad(rect.bottomRight, rect.bottomLeft, rect.topLeft, rect.topRight)
        assertEquals(5000.0, reversed.area, 1e-9)
    }

    @Test
    fun convexityRejectsBowTiesAndCollinearPoints() {
        assertTrue(rect.isConvex)
        assertFalse(Quad(Point(0f, 0f), Point(100f, 50f), Point(100f, 0f), Point(0f, 50f)).isConvex)
        assertFalse(Quad(Point(0f, 0f), Point(50f, 0f), Point(100f, 0f), Point(0f, 50f)).isConvex)
    }

    @Test
    fun wireFormatRoundTrips() {
        val values = rect.toFloatArray()
        assertContentEquals(floatArrayOf(0f, 0f, 100f, 0f, 100f, 50f, 0f, 50f), values)
        assertEquals(rect, Quad.fromDoubles(DoubleArray(8) { values[it].toDouble() }))
        assertEquals(listOf(rect.topLeft, rect.topRight, rect.bottomRight, rect.bottomLeft), rect.points)
    }

    @Test
    fun fullFrameCoversThePhoto() {
        val frame = Quad.fullFrame(4000, 3000)
        assertEquals(Point(4000f, 3000f), frame.bottomRight)
        assertEquals(12_000_000.0, frame.area, 1e-6)
    }
}
