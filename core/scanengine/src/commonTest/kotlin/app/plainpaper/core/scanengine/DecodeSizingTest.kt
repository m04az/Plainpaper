package app.plainpaper.core.scanengine

import kotlin.test.Test
import kotlin.test.assertEquals

class DecodeSizingTest {
    @Test
    fun smallPhotosAreNotSampled() {
        assertEquals(1, DecodeSizing.sampleSize(4000, 3000, 12_000_000))
        assertEquals(PhotoSize(4000, 3000), DecodeSizing.sampledSize(4000, 3000, 12_000_000))
    }

    @Test
    fun bigPhotosAreSampledByPowersOfTwo() {
        // 50 MP: sqrt(50/12) = 2.04 -> 4
        assertEquals(4, DecodeSizing.sampleSize(8000, 6250, 12_000_000))
        assertEquals(PhotoSize(2000, 1563), DecodeSizing.sampledSize(8000, 6250, 12_000_000))
        // 13 MP: sqrt(13/12) = 1.04 -> 2
        assertEquals(2, DecodeSizing.sampleSize(4160, 3120, 12_000_000))
        assertEquals(PhotoSize(2080, 1560), DecodeSizing.sampledSize(4160, 3120, 12_000_000))
    }

    @Test
    fun sampledSizeRoundsUpLikeTheDecoders() {
        assertEquals(PhotoSize(251, 126), DecodeSizing.sampledSize(1001, 501, 125_000))
    }

    @Test
    fun thumbnailEdgeIsTheLongEdgeOfA43PhotoWithThatManyPixels() {
        assertEquals(4000, DecodeSizing.thumbnailMaxEdge(12_000_000))
    }

    @Test
    fun fitLongEdgeScalesDownOnlyAndKeepsTheAspect() {
        assertEquals(PhotoSize(1600, 1200), DecodeSizing.fitLongEdge(4000, 3000, 1600))
        assertEquals(PhotoSize(1200, 1600), DecodeSizing.fitLongEdge(3000, 4000, 1600))
        assertEquals(PhotoSize(800, 600), DecodeSizing.fitLongEdge(800, 600, 1600))
    }

    @Test
    fun editorAndEngineSizesDifferByAnExactScale() {
        // The engine decodes 4160x3120 at half size; the editor shows it at 1600 px. Corners map by the ratio.
        val engine = DecodeSizing.sampledSize(4160, 3120, 12_000_000)
        val editor = DecodeSizing.fitLongEdge(4160, 3120, 1600)
        assertEquals(1600, editor.width)
        assertEquals(2080f / 1600f, engine.width.toFloat() / editor.width)
    }
}
