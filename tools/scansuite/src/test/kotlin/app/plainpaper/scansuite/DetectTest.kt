package app.plainpaper.scansuite

import app.plainpaper.core.domain.model.Confidence
import app.plainpaper.core.scanengine.ByteArrayPreviewFrame
import app.plainpaper.core.scanengine.PixelFormat
import app.plainpaper.core.scanengine.model.DetectResult
import kotlinx.coroutines.runBlocking
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `pp_detect` on preview-sized frames (specs/003 contracts/c-api-detect.md, quickstart §2). */
class DetectTest {
    private val engine = SuiteRunner(File(SuiteResults.outputDir, "detect")).engine
    private val synthetic = SuiteResults.sources.first { it.synthetic }

    private data class Scaled(
        val frame: ByteArrayPreviewFrame,
        val scale: Double,
    )

    private fun load(
        photo: ReferencePhoto,
        format: PixelFormat = PixelFormat.RGBA,
    ): Scaled {
        val image = ImageIO.read(File(synthetic.root, photo.file))
        val scale = PREVIEW_LONG_EDGE.toDouble() / maxOf(image.width, image.height)
        val width = (image.width * scale).toInt()
        val height = (image.height * scale).toInt()
        val small = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        small.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(image, 0, 0, width, height, null)
            dispose()
        }
        val bytes = ByteArray(width * height * 4)
        val row = IntArray(width)
        for (y in 0 until height) {
            small.getRGB(0, y, width, 1, row, 0, width)
            for (x in 0 until width) {
                val argb = row[x]
                val i = (y * width + x) * 4
                val r = (argb shr 16).toByte()
                val g = (argb shr 8).toByte()
                val b = argb.toByte()
                if (format == PixelFormat.RGBA) {
                    bytes[i] = r
                    bytes[i + 2] = b
                } else {
                    bytes[i] = b
                    bytes[i + 2] = r
                }
                bytes[i + 1] = g
                bytes[i + 3] = (argb ushr 24).toByte()
            }
        }
        return Scaled(ByteArrayPreviewFrame(width, height, width * 4, format, 0L, bytes), scale)
    }

    private fun detect(scaled: Scaled): DetectResult = runBlocking { engine.detect(scaled.frame) }

    private fun cornerError(
        photo: ReferencePhoto,
        scaled: Scaled,
        result: DetectResult,
    ): Double {
        val expected = photo.expectedCorners!!.map { (x, y) -> x * scaled.scale to y * scaled.scale }
        val found = result.corners!!.points.map { it.x.toDouble() to it.y.toDouble() }
        val diagonal = hypot(scaled.frame.width.toDouble(), scaled.frame.height.toDouble())
        // Expected corners are of the upright page; the preview quad may start at another corner.
        return (0 until 4).minOf { shift ->
            (0 until 4).maxOf { i ->
                val (ex, ey) = expected[i]
                val (fx, fy) = found[(i + shift) % 4]
                hypot(ex - fx, ey - fy) / diagonal
            }
        }
    }

    @Test
    fun plainScenesAreFoundWithHighConfidence() {
        val photos =
            synthetic.manifest.photos.filter { photo ->
                photo.expectedCorners != null && photo.tags.none { it in HARD }
            }
        assertTrue(photos.size >= 12, "expected the plain synthetic scenes, got ${photos.size}")
        val micros = mutableListOf<Long>()
        for (photo in photos) {
            val scaled = load(photo)
            val result = detect(scaled)
            micros += result.micros
            assertEquals(Confidence.High, result.confidence, photo.id)
            val error = cornerError(photo, scaled, result)
            assertTrue(error <= CORNER_TOLERANCE, "${photo.id}: corner error $error")
            assertTrue(result.fillFraction in 0.2f..1f, "${photo.id}: fill ${result.fillFraction}")
        }
        println("pp_detect host median: ${micros.sorted()[micros.size / 2] / 1000.0} ms over ${micros.size} frames")
    }

    @Test
    fun noPageScenesAreNotFound() {
        val photos = synthetic.manifest.photos.filter { Category.NoPage in it.tags }
        assertTrue(photos.isNotEmpty())
        for (photo in photos) {
            val result = detect(load(photo))
            assertEquals(Confidence.NotFound, result.confidence, photo.id)
            assertEquals(null, result.corners, photo.id)
            assertEquals(0f, result.fillFraction, photo.id)
        }
    }

    @Test
    fun veryDarkScenesReportLowLuma() {
        val photo = synthetic.manifest.photos.first { it.id.contains("verydark") }
        val result = detect(load(photo))
        assertTrue(result.meanLuma < DARK_LUMA, "${photo.id}: mean luma ${result.meanLuma}")
    }

    @Test
    fun bgraMatchesRgba() {
        val photo = synthetic.manifest.photos.first { it.expectedCorners != null && it.tags.none { t -> t in HARD } }
        val rgba = detect(load(photo, PixelFormat.RGBA))
        val bgra = detect(load(photo, PixelFormat.BGRA))
        assertEquals(rgba.confidence, bgra.confidence)
        val rgbaPts = rgba.corners!!.points
        val bgraPts = bgra.corners!!.points
        for (i in 0 until 4) {
            assertTrue(hypot((rgbaPts[i].x - bgraPts[i].x).toDouble(), (rgbaPts[i].y - bgraPts[i].y).toDouble()) <= 2.0)
        }
    }

    private companion object {
        const val PREVIEW_LONG_EDGE = 640
        const val CORNER_TOLERANCE = 0.02
        const val DARK_LUMA = 40f
        val HARD = setOf(Category.NoPage, Category.Curled, Category.DarkRoom, Category.LowContrastBackground)
    }
}
