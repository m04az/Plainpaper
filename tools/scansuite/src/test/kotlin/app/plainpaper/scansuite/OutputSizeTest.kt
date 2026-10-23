package app.plainpaper.scansuite

import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.BufferedImageScanOutput
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.ImageIoPhotoDecoder
import app.plainpaper.core.scanengine.RenderResult
import app.plainpaper.core.scanengine.model.RenderOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** FR-008: every rendered output has exactly the page-size pixel dimensions (US1). */
class OutputSizeTest {
    @Test
    fun everyPageSizeAndStyleHasTheContractDimensions() =
        runBlocking {
            val synthetic = SuiteResults.sources.first { it.synthetic }
            val photo = synthetic.manifest.photos.first { it.expectedRotation == 0 }
            val decoder = ImageIoPhotoDecoder(HostDispatchers())
            val decoded = assertIs<DecodeResult.Ready>(decoder.decode(File(synthetic.root, photo.file).path)).photo
            val engine = SuiteRunner(File(SuiteResults.outputDir, "sizes")).engine
            val analysis = assertIs<AnalyzeResult.Ready>(engine.analyze(decoded)).analysis
            analysis.use {
                for (pageSize in PageSize.entries) {
                    for (style in OutputStyle.entries) {
                        val output =
                            assertIs<RenderResult.Ready>(
                                engine.render(analysis, RenderOptions(style, pageSize)),
                            ).output
                        val expected = pageSize.outputPixels(analysis.detectedAspect, analysis.isLandscape)
                        assertEquals(expected.width, output.width, "$pageSize $style width")
                        assertEquals(expected.height, output.height, "$pageSize $style height")
                        assertEquals(style.channels, output.channels, "$pageSize $style channels")
                        val image = assertIs<BufferedImageScanOutput>(output).image
                        assertTrue(image.width == output.width && image.height == output.height)
                    }
                }
            }
        }
}
