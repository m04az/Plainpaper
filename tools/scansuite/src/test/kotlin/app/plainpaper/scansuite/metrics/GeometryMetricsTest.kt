package app.plainpaper.scansuite.metrics

import app.plainpaper.core.scanengine.model.Point
import app.plainpaper.core.scanengine.model.Quad
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeometryMetricsTest {
    @Test
    fun cornerErrorIsZeroForIdenticalQuadsInAnyCyclicOrder() {
        val expected = listOf(listOf(100.0, 100.0), listOf(900.0, 110.0), listOf(890.0, 700.0), listOf(110.0, 690.0))
        val detected = Quad(Point(900f, 110f), Point(890f, 700f), Point(110f, 690f), Point(100f, 100f))
        assertEquals(0.0, CornerError.fractionOfDiagonal(expected, detected, 1000, 800), 1e-9)
    }

    @Test
    fun cornerErrorIsTheWorstCornerOverTheDiagonal() {
        val expected = listOf(listOf(0.0, 0.0), listOf(1000.0, 0.0), listOf(1000.0, 800.0), listOf(0.0, 800.0))
        val detected = Quad(Point(0f, 0f), Point(1000f, 0f), Point(1000f, 800f), Point(30f, 840f))
        val diagonal = kotlin.math.hypot(1000.0, 800.0)
        assertEquals(50.0 / diagonal, CornerError.fractionOfDiagonal(expected, detected, 1000, 800), 1e-9)
    }

    @Test
    fun toneMetricsOnASyntheticPage() {
        val image = BufferedImage(400, 300, BufferedImage.TYPE_BYTE_GRAY)
        val g = image.createGraphics()
        g.color = Color(250, 250, 250)
        g.fillRect(0, 0, 400, 300)
        g.color = Color(20, 20, 20)
        for (line in 0 until 6) g.fillRect(40, 40 + line * 40, 320, 6)
        g.dispose()
        val gray = GrayImage.of(image)
        assertEquals(250 / 255.0, ToneMetrics.whiteness(gray), 1e-9)
        val darkness = assertNotNull(ToneMetrics.textDarkness(gray))
        assertEquals(20 / 255.0, darkness, 1e-9)
    }

    @Test
    fun blankPageHasNoTextDarkness() {
        val image = BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 100, 100)
        g.dispose()
        assertNull(ToneMetrics.textDarkness(GrayImage.of(image)))
    }

    @Test
    fun straightnessRecoversASmallRotation() {
        val image = BufferedImage(800, 600, BufferedImage.TYPE_BYTE_GRAY)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 800, 600)
        g.color = Color.BLACK
        g.rotate(Math.toRadians(1.5), 400.0, 300.0)
        for (line in 0 until 12) g.fillRect(100, 60 + line * 40, 600, 8)
        g.dispose()
        val skew = Straightness.skewDegrees(GrayImage.of(image))
        assertTrue(abs(abs(skew) - 1.5) <= 0.2, "skew $skew")
    }

    @Test
    fun orientationIsCorrectModulo360() {
        assertTrue(OrientationMetric.isCorrect(expectedRotation = 90, rotationApplied = 90))
        assertTrue(OrientationMetric.isCorrect(expectedRotation = 0, rotationApplied = 0))
        assertTrue(!OrientationMetric.isCorrect(expectedRotation = 180, rotationApplied = 0))
    }
}
