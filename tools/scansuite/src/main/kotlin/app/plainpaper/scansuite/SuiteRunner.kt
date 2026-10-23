package app.plainpaper.scansuite

import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.BufferedImageScanOutput
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.DefaultScanEngine
import app.plainpaper.core.scanengine.HostCapabilityProvider
import app.plainpaper.core.scanengine.ImageIoPhotoDecoder
import app.plainpaper.core.scanengine.RenderResult
import app.plainpaper.core.scanengine.ScanAnalysis
import app.plainpaper.core.scanengine.ScanEngine
import app.plainpaper.core.scanengine.ScanOutput
import app.plainpaper.core.scanengine.bridge.platformNativeBridge
import app.plainpaper.core.scanengine.decision.PageFraming
import app.plainpaper.core.scanengine.model.AnalyzeOptions
import app.plainpaper.core.scanengine.model.Confidence
import app.plainpaper.core.scanengine.model.Outcome
import app.plainpaper.core.scanengine.model.ProcessingStep
import app.plainpaper.core.scanengine.model.RenderOptions
import app.plainpaper.core.scanengine.model.StepStatus
import app.plainpaper.scansuite.metrics.CornerError
import app.plainpaper.scansuite.metrics.GrayImage
import app.plainpaper.scansuite.metrics.OrientationMetric
import app.plainpaper.scansuite.metrics.Straightness
import app.plainpaper.scansuite.metrics.ToneMetrics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import javax.imageio.ImageIO

/** A manifest with the directory its paths are relative to. */
data class ManifestSource(
    val root: File,
    val manifest: Manifest,
    val synthetic: Boolean,
)

class HostDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}

/** Runs the engine over manifests, writes outputs and computes metric rows. */
class SuiteRunner(
    private val outputDir: File,
    private val capability: DeviceCapability = HostCapabilityProvider().current(),
    private val styles: Set<OutputStyle> = setOf(OutputStyle.Scan),
    private val legibility: LegibilityScorer? = null,
) {
    private val dispatchers = HostDispatchers()
    val engine: ScanEngine = DefaultScanEngine(platformNativeBridge(), HostCapabilityProvider(), dispatchers)
    private val decoder = ImageIoPhotoDecoder(dispatchers)

    fun run(sources: List<ManifestSource>): QualityReport {
        val rows = mutableListOf<MetricRow>()
        for (source in sources) {
            val problems = source.manifest.validate(source.root)
            require(problems.isEmpty()) { "Invalid manifest in ${source.root}:\n" + problems.joinToString("\n") }
            for (photo in source.manifest.photos) rows += runPhoto(source, photo)
        }
        val thresholds = Thresholds.load()
        val report =
            QualityReport(
                generatedAt = Instant.now().toString(),
                engineVersion = gitSha(),
                capability = capability.name,
                rows = rows,
                checks = Gate.evaluate(rows, thresholds),
                competitorSummary = CompetitorComparison.summarise(rows, thresholds),
            )
        report.write(outputDir)
        return report
    }

    fun runPhoto(
        source: ManifestSource,
        photo: ReferencePhoto,
    ): MetricRow =
        runBlocking {
            val base =
                MetricRow(
                    photo.id,
                    photo.tags,
                    photo.language,
                    source.synthetic,
                    outcome = "",
                    expectedRotation = photo.expectedRotation,
                )
            val file = File(source.root, photo.file)
            val decoded =
                when (val result = decoder.decode(file.path)) {
                    is DecodeResult.Ready -> result.photo

                    is DecodeResult.Failed -> return@runBlocking base.copy(
                        outcome = "decode-${result.reason}",
                        error = "decode failed",
                    )
                }
            val start = System.nanoTime()
            val analysis =
                when (val result = engine.analyze(decoded, AnalyzeOptions(capability = capability))) {
                    is AnalyzeResult.Ready -> result.analysis

                    is AnalyzeResult.Failed -> return@runBlocking base.copy(
                        outcome = result.outcome.toString(),
                        error = "analyze failed",
                    )
                }
            analysis.use {
                val outputs = mutableMapOf<OutputStyle, ScanOutput>()
                for (style in styles) {
                    when (val rendered = engine.render(analysis, RenderOptions(style, PageSize.A4))) {
                        is RenderResult.Ready -> outputs[style] = rendered.output

                        is RenderResult.Failed -> return@runBlocking base.copy(
                            outcome = rendered.outcome.toString(),
                            error = "render $style failed",
                        )
                    }
                }
                val millis = (System.nanoTime() - start) / 1_000_000
                for ((style, output) in outputs) save(photo.id, style, output)
                metrics(
                    PhotoRun(base, photo, source, ImageSize(decoded.width, decoded.height), analysis, outputs, millis),
                )
            }
        }

    /** Everything measured for one photo. */
    private class PhotoRun(
        val base: MetricRow,
        val photo: ReferencePhoto,
        val source: ManifestSource,
        val photoSize: ImageSize,
        val analysis: ScanAnalysis,
        val outputs: Map<OutputStyle, ScanOutput>,
        val millis: Long,
    )

    private fun metrics(run: PhotoRun): MetricRow {
        val base = run.base
        val photo = run.photo
        val source = run.source
        val analysis = run.analysis
        val outputs = run.outputs
        val scan = outputs[OutputStyle.Scan] as? BufferedImageScanOutput
        val scanGray = scan?.let { pageRegion(it, analysis) }
        val hasText = photo.language != Language.None
        val cornerError =
            photo.expectedCorners?.let {
                CornerError.fractionOfDiagonal(
                    it,
                    analysis.corners,
                    run.photoSize.width,
                    run.photoSize.height,
                )
            }
        val flatten = analysis.steps.firstOrNull { it.step == ProcessingStep.Flatten }?.status
        val legibilityScore = scan?.let { scoreLegibility(source, photo, it) }
        return base.copy(
            outcome = (analysis.outcome as? Outcome.TooDark)?.let { "TooDark" } ?: "Success",
            confidence = analysis.confidence.name,
            cornerError = cornerError,
            classifiedNotFound =
                if (Category.NoPage in
                    photo.tags
                ) {
                    analysis.confidence == Confidence.NotFound
                } else {
                    null
                },
            whiteness = scanGray?.let { ToneMetrics.whiteness(it) },
            textDarkness = scanGray?.let { ToneMetrics.textDarkness(it) },
            legibility = legibilityScore,
            straightnessDeg =
                if (hasText) {
                    scanGray?.let {
                        Straightness.baselineDeviationDegrees(it)
                            ?: Straightness.skewDegrees(it)
                    }
                } else {
                    null
                },
            rotationApplied = analysis.rotationApplied,
            orientationCorrect =
                if (hasText) {
                    OrientationMetric.isCorrect(
                        photo.expectedRotation,
                        analysis.rotationApplied,
                    )
                } else {
                    null
                },
            hostMillis = run.millis,
            peakNativeBytes = analysis.peakNativeBytes,
            flattenStatus = flatten?.let { statusName(it) },
            competitor = CompetitorComparison.measure(source, photo, scan, legibility),
        )
    }

    private fun scoreLegibility(
        source: ManifestSource,
        photo: ReferencePhoto,
        scan: BufferedImageScanOutput,
    ): Double? {
        val scorer = legibility
        val transcript = photo.transcript
        val clean = photo.cleanReference
        if (scorer == null || transcript == null || clean == null) return null
        return scorer.relativeScore(scan.image, File(source.root, clean), File(source.root, transcript), photo.language)
    }

    private fun statusName(status: StepStatus): String =
        when (status) {
            StepStatus.Applied -> "Applied"
            is StepStatus.Skipped -> "Skipped(${status.reason})"
            is StepStatus.Failed -> "Failed(${status.reason})"
        }

    /** Grey copy of the page area inside the output frame (margins excluded). */
    private fun pageRegion(
        output: BufferedImageScanOutput,
        analysis: ScanAnalysis,
    ): GrayImage {
        val rect =
            PageFraming.contentRect(
                analysis.detectedAspect,
                app.plainpaper.core.domain.model
                    .PixelSize(output.width, output.height),
            )
        // Shave 3% off every edge so warp borders do not count as paper or ink.
        val insetX = (rect.width * 0.03).toInt()
        val insetY = (rect.height * 0.03).toInt()
        return GrayImage.of(
            output.image,
            rect.x + insetX,
            rect.y + insetY,
            rect.width - 2 * insetX,
            rect.height - 2 * insetY,
        )
    }

    private fun save(
        id: String,
        style: OutputStyle,
        output: ScanOutput,
    ) {
        val image = (output as? BufferedImageScanOutput)?.image ?: return
        val dir = File(outputDir, "outputs").apply { mkdirs() }
        ImageIO.write(image, "png", File(dir, "$id-${style.name.lowercase()}.png"))
    }

    private fun gitSha(): String =
        runCatching {
            ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
        }.getOrDefault("unknown")

    companion object {
        /** Real manifest at `reference-set/` plus the generated synthetic set. */
        fun defaultSources(
            rootDir: File,
            buildDir: File,
        ): List<ManifestSource> {
            val realRoot = File(rootDir, "reference-set")
            val real = ManifestSource(realRoot, Manifest.load(File(realRoot, "manifest.json")), synthetic = false)
            val synthetic = SyntheticSet.ensure(rootDir, File(buildDir, "synthetic"))
            return listOf(real, synthetic)
        }
    }
}
