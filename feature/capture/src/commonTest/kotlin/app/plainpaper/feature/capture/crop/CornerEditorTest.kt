package app.plainpaper.feature.capture.crop

import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CornerEditorTest {
    // 4000 x 3000 photo shown in a 400 x 400 view: scale 0.1, 50px bars above and below.
    private val border = Quad(Point(400f, 300f), Point(3600f, 300f), Point(3600f, 2700f), Point(400f, 2700f))

    private fun editor(
        quad: Quad = border,
        detected: Quad? = null,
        viewWidth: Float = 400f,
        viewHeight: Float = 400f,
    ) = CornerEditor(4000, 3000, viewWidth, viewHeight, quad, detected)

    private fun assertPoint(
        expectedX: Float,
        expectedY: Float,
        actual: Point,
    ) {
        assertEquals(expectedX, actual.x, 0.01f)
        assertEquals(expectedY, actual.y, 0.01f)
    }

    @Test
    fun theImageIsFittedAndCentredInTheView() {
        val fit = editor().fit
        assertEquals(0.1f, fit.scale, 1e-6f)
        assertEquals(0f, fit.left, 1e-3f)
        assertEquals(50f, fit.top, 1e-3f)
        assertEquals(400f, fit.width, 1e-3f)
        assertEquals(300f, fit.height, 1e-3f)
        val wide = editor(viewWidth = 1000f, viewHeight = 300f).fit
        assertEquals(0.1f, wide.scale, 1e-6f)
        assertEquals(300f, wide.left, 1e-3f)
        assertEquals(0f, wide.top, 1e-3f)
    }

    @Test
    fun mappingRoundTrips() {
        val editor = editor()
        assertPoint(40f, 80f, editor.toView(Point(400f, 300f)))
        assertPoint(0f, 50f, editor.toView(Point(0f, 0f)))
        assertPoint(400f, 350f, editor.toView(Point(4000f, 3000f)))
        assertPoint(1234f, 2345f, editor.toImage(editor.toView(Point(1234f, 2345f))))
        assertEquals(border.points.map { editor.toView(it) }, editor.viewCorners())
    }

    @Test
    fun dragMovesOnlyThatCornerByTheViewDeltaScaledToImagePixels() {
        val moved = editor().drag(0, Point(10f, 20f))
        assertPoint(500f, 500f, moved.quad.topLeft)
        assertEquals(border.topRight, moved.quad.topRight)
        assertEquals(border.bottomRight, moved.quad.bottomRight)
        assertEquals(border.bottomLeft, moved.quad.bottomLeft)
        assertEquals(border, editor().quad, "the editor is immutable")
    }

    @Test
    fun dragIsClampedToTheImage() {
        assertPoint(0f, 0f, editor().drag(0, Point(-500f, -500f)).quad.topLeft)
        assertPoint(4000f, 3000f, editor().drag(2, Point(500f, 500f)).quad.bottomRight)
    }

    @Test
    fun dragRejectsAConcaveQuad() {
        val editor = editor()
        // Pull the top-left corner past the opposite diagonal: the quad would fold in on itself.
        assertSame(editor.quad, editor.drag(0, Point(300f, 200f)).quad)
    }

    @Test
    fun dragRejectsASelfIntersectingQuad() {
        val editor = editor()
        // Top-left far below the bottom-left corner.
        assertSame(editor.quad, editor.drag(0, Point(0f, 260f)).quad)
    }

    @Test
    fun dragRejectsAQuadSmallerThanFivePercentOfThePhoto() {
        // 700k px of a 12M px photo is 5.8%.
        val big = Quad(Point(1000f, 1000f), Point(2000f, 1000f), Point(2000f, 1700f), Point(1000f, 1700f))
        val start = editor(big)
        assertTrue(start.quad.area >= 0.05 * 4000 * 3000)
        assertSame(start.quad, start.drag(1, Point(-30f, 0f)).quad) // 300px left: 490k px, 4.1%
        assertEquals(
            1980f,
            start
                .drag(1, Point(-2f, 0f))
                .quad.topRight.x,
            0.01f,
        )
    }

    @Test
    fun autoReturnsTheDetectedBorderOrKeepsTheCurrentOne() {
        val detected = Quad(Point(10f, 20f), Point(3900f, 30f), Point(3890f, 2900f), Point(20f, 2950f))
        assertEquals(detected, editor(detected = detected).drag(0, Point(10f, 10f)).auto().quad)
        assertEquals(border, editor(detected = null).auto().quad)
    }

    @Test
    fun fullPhotoUsesTheImageCornersInTheSameRoles() {
        assertEquals(Quad.fullFrame(4000, 3000), editor().fullPhoto().quad)
    }

    @Test
    fun fullPhotoKeepsRolesOfAPageRotatedBy90Degrees() {
        // Reading order of a page whose top-left (role 0) sits at the photo's bottom-left.
        val rotated = Quad(Point(400f, 2700f), Point(400f, 300f), Point(3600f, 300f), Point(3600f, 2700f))
        val full = editor(rotated).fullPhoto().quad
        assertEquals(Point(0f, 3000f), full.topLeft)
        assertEquals(Point(0f, 0f), full.topRight)
        assertEquals(Point(4000f, 0f), full.bottomRight)
        assertEquals(Point(4000f, 3000f), full.bottomLeft)
        assertTrue(full.isConvex)
    }

    @Test
    fun fullPhotoKeepsRolesOfAMirroredPage() {
        val mirrored = Quad(Point(3600f, 300f), Point(400f, 300f), Point(400f, 2700f), Point(3600f, 2700f))
        val full = editor(mirrored).fullPhoto().quad
        assertEquals(Point(4000f, 0f), full.topLeft)
        assertEquals(Point(0f, 0f), full.topRight)
        assertEquals(Point(0f, 3000f), full.bottomRight)
        assertEquals(Point(4000f, 3000f), full.bottomLeft)
    }

    @Test
    fun fullPhotoOfATiltedPageIsTheImageCornersInOrder() {
        val tilted = Quad(Point(700f, 100f), Point(3900f, 500f), Point(3300f, 2900f), Point(100f, 2400f))
        assertEquals(Quad.fullFrame(4000, 3000), editor(tilted).fullPhoto().quad)
    }

    @Test
    fun resultIsInImagePixels() {
        assertPoint(500f, 300f, editor().drag(0, Point(10f, 0f)).result().topLeft)
    }

    @Test
    fun handleHitTestPicksTheNearestCornerWithinTheRadius() {
        val editor = editor()
        assertEquals(0, editor.cornerAt(Point(45f, 85f), radius = 24f))
        assertEquals(2, editor.cornerAt(Point(355f, 315f), radius = 24f))
        assertNull(editor.cornerAt(Point(200f, 200f), radius = 24f))
    }
}
