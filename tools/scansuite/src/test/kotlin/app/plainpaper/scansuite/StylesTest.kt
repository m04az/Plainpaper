package app.plainpaper.scansuite

import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.PixelSize
import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.BufferedImageScanOutput
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.ImageIoPhotoDecoder
import app.plainpaper.core.scanengine.RenderResult
import app.plainpaper.core.scanengine.ScanAnalysis
import app.plainpaper.core.scanengine.decision.PageFraming
import app.plainpaper.core.scanengine.model.RenderOptions
import app.plainpaper.scansuite.metrics.GrayImage
import app.plainpaper.scansuite.metrics.ToneMetrics
import app.plainpaper.scansuite.synthetic.SyntheticSetGenerator
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** User Story 2: the four styles share geometry and differ as specified (FR-009). */
class StylesTest {
    private val engine = SuiteRunner(File(SuiteResults.outputDir, "styles")).engine

    private fun analyzeStampPhoto(): Pair<ScanAnalysis, Map<OutputStyle, BufferedImageScanOutput>> =
        runBlocking {
            val synthetic = SuiteResults.sources.first { it.synthetic }
            val photo =
                synthetic.manifest.photos.first {
                    Category.StampSignature in it.tags &&
                        it.language == Language.English
                }
            val decoded =
                assertIs<DecodeResult.Ready>(
                    ImageIoPhotoDecoder(HostDispatchers()).decode(File(synthetic.root, photo.file).path),
                ).photo
            val analysis = assertIs<AnalyzeResult.Ready>(engine.analyze(decoded)).analysis
            val outputs =
                OutputStyle.entries.associateWith { style ->
                    assertIs<BufferedImageScanOutput>(
                        assertIs<RenderResult.Ready>(engine.render(analysis, RenderOptions(style, PageSize.A4))).output,
                    )
                }
            analysis to outputs
        }

    @Test
    fun allStylesShareDimensionsAndChannels() {
        val (analysis, outputs) = analyzeStampPhoto()
        analysis.use {
            for ((style, output) in outputs) {
                assertEquals(2480, output.width, "$style")
                assertEquals(3508, output.height, "$style")
                assertEquals(style.channels, output.channels, "$style")
                assertEquals(style, output.styleUsed)
            }
        }
    }

    @Test
    fun grayscaleKeepsTonalDetail() {
        val (analysis, outputs) = analyzeStampPhoto()
        analysis.use {
            val gray = GrayImage.of(outputs.getValue(OutputStyle.Grayscale).image)
            val levels = gray.histogram().count { it > 0 }
            assertTrue(levels > 16, "grayscale levels $levels")
            assertTrue(ToneMetrics.whiteness(gray) >= 0.97)
        }
    }

    @Test
    fun colorDocumentKeepsTheStampRedOnWhitePaper() {
        val (analysis, outputs) = analyzeStampPhoto()
        analysis.use {
            val output = outputs.getValue(OutputStyle.ColorDocument)
            val rect = PageFraming.contentRect(analysis.detectedAspect, PixelSize(output.width, output.height))
            // Map the stamp centre from page coordinates to the output.
            val scale = rect.width.toDouble() / SyntheticSetGenerator.PAGE_WIDTH
            val cx = rect.x + SyntheticSetGenerator.STAMP_CENTER_X * scale
            val cy = rect.y + SyntheticSetGenerator.STAMP_CENTER_Y * scale
            val radius = SyntheticSetGenerator.STAMP_RADIUS * scale
            val (colored, hueOk) = countStampPixels(output.image, cx, cy, radius)
            assertTrue(colored > 500, "coloured stamp pixels: $colored")
            assertTrue(hueOk >= colored * 0.9, "red hue retained on $hueOk of $colored pixels")
            val page = GrayImage.of(output.image, rect.x, rect.y, rect.width, rect.height)
            assertTrue(ToneMetrics.whiteness(page) >= 0.97, "paper white in Color document")
        }
    }

    /** Counts coloured pixels inside the stamp circle and how many of them keep a red hue. */
    private fun countStampPixels(
        image: java.awt.image.BufferedImage,
        cx: Double,
        cy: Double,
        radius: Double,
    ): Pair<Int, Int> {
        var colored = 0
        var hueOk = 0
        for (y in (cy - radius).toInt()..(cy + radius).toInt()) {
            for (x in (cx - radius).toInt()..(cx + radius).toInt()) {
                val kind = if (hypot(x - cx, y - cy) <= radius) classify(image.getRGB(x, y)) else PixelKind.Paper
                if (kind != PixelKind.Paper) colored++
                if (kind == PixelKind.Red) hueOk++
            }
        }
        return colored to hueOk
    }

    private enum class PixelKind { Paper, Colored, Red }

    private fun classify(rgb: Int): PixelKind {
        val hsv = FloatArray(3)
        Color.RGBtoHSB((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, hsv)
        if (hsv[1] <= 0.3f || hsv[2] <= 0.2f) return PixelKind.Paper
        val hueDeg = hsv[0] * 360f
        return if (minOf(hueDeg, 360f - hueDeg) <= 15f) PixelKind.Red else PixelKind.Colored
    }

    @Test
    fun originalKeepsTheBackgroundInsteadOfForcingWhite() {
        val (analysis, outputs) = analyzeStampPhoto()
        analysis.use {
            val original = outputs.getValue(OutputStyle.Original)
            val scan = outputs.getValue(OutputStyle.Scan)
            val rect = PageFraming.contentRect(analysis.detectedAspect, PixelSize(original.width, original.height))
            val originalWhite =
                ToneMetrics.whiteness(
                    GrayImage.of(original.image, rect.x, rect.y, rect.width, rect.height),
                )
            val scanWhite = ToneMetrics.whiteness(GrayImage.of(scan.image, rect.x, rect.y, rect.width, rect.height))
            assertTrue(originalWhite < scanWhite, "original $originalWhite vs scan $scanWhite")
            assertTrue(originalWhite > 0.6, "original stays light: $originalWhite")
            assertNotNull(ToneMetrics.textDarkness(GrayImage.of(scan.image, rect.x, rect.y, rect.width, rect.height)))
        }
    }

    @Test
    fun renderingTwiceReusesTheGeometry() {
        val (analysis, outputs) = analyzeStampPhoto()
        analysis.use {
            val corners = analysis.corners
            runBlocking {
                val again =
                    assertIs<RenderResult.Ready>(
                        engine.render(analysis, RenderOptions(OutputStyle.Scan, PageSize.A4)),
                    ).output
                assertEquals(outputs.getValue(OutputStyle.Scan).width, again.width)
                assertEquals(corners, analysis.corners)
                assertTrue(abs(analysis.detectedAspect - analysis.detectedAspect) < 1e-12)
            }
        }
    }
}
