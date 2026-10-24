package app.plainpaper.scansuite

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.BufferedImageScanOutput
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.ImageIoPhotoDecoder
import app.plainpaper.core.scanengine.RenderResult
import app.plainpaper.core.scanengine.model.AnalyzeOptions
import app.plainpaper.core.scanengine.model.RenderOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO

/**
 * Developer tool: runs the engine on every JPEG/PNG in [photosDir] (e.g. photos pulled from a
 * phone) and writes `<name>-<style>.png` plus a one-line summary per photo. Not a gate.
 * `./gradlew :tools:scansuite:run --args="--photos /path/to/dir [--simplified]"`
 */
object AdHoc {
    fun run(
        photosDir: File,
        outputDir: File,
        capability: DeviceCapability,
    ) = runBlocking {
        val runner = SuiteRunner(outputDir, capability)
        val decoder = ImageIoPhotoDecoder(HostDispatchers())
        outputDir.mkdirs()
        val photos =
            photosDir
                .listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }
                .orEmpty()
                .sortedBy { it.name }
        for (file in photos) {
            val photo = (decoder.decode(file.path) as? DecodeResult.Ready)?.photo
            if (photo == null) {
                println("${file.name}: decode failed")
                continue
            }
            val started = System.nanoTime()
            when (val result = runner.engine.analyze(photo, AnalyzeOptions(capability = capability))) {
                is AnalyzeResult.Failed -> println("${file.name}: ${result.outcome}")
                is AnalyzeResult.Ready ->
                    result.analysis.use { analysis ->
                        val ms = (System.nanoTime() - started) / NANOS_PER_MILLI
                        println(
                            "${file.name}: ${analysis.confidence} ${analysis.cornerSource} rot=${analysis.rotationApplied} " +
                                "aspect=${"%.3f".format(analysis.detectedAspect)} outcome=${analysis.outcome} ${ms}ms " +
                                "corners=${analysis.corners.points.joinToString { "(${it.x.toInt()},${it.y.toInt()})" }}",
                        )
                        for (style in OutputStyle.entries) {
                            val rendered = runner.engine.render(analysis, RenderOptions(style, PageSize.A4))
                            val image = ((rendered as? RenderResult.Ready)?.output as? BufferedImageScanOutput)?.image ?: continue
                            ImageIO.write(image, "png", File(outputDir, "${file.nameWithoutExtension}-${style.name.lowercase()}.png"))
                        }
                    }
            }
        }
        println("Outputs in $outputDir")
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
