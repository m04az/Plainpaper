package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.bridge.NativeSession
import app.plainpaper.core.scanengine.model.AnalysisFlag
import app.plainpaper.core.scanengine.model.AnalyzeOptions
import app.plainpaper.core.scanengine.model.Confidence
import app.plainpaper.core.scanengine.model.CornerSource
import app.plainpaper.core.scanengine.model.DetectResult
import app.plainpaper.core.scanengine.model.OrientationConfidence
import app.plainpaper.core.scanengine.model.Outcome
import app.plainpaper.core.scanengine.model.Quad
import app.plainpaper.core.scanengine.model.RenderOptions
import app.plainpaper.core.scanengine.model.StepTiming

/** The scan pipeline façade (contracts/kotlin-api.md). */
public interface ScanEngine {
    /** Detect, correct and normalise one photo. Runs on the default dispatcher; cancellable. */
    public suspend fun analyze(
        photo: ScanPhoto,
        options: AnalyzeOptions = AnalyzeOptions(),
    ): AnalyzeResult

    /** Render an analysed page in a style and page size. Reuses the geometry; cancellable. */
    public suspend fun render(
        analysis: ScanAnalysis,
        options: RenderOptions,
    ): RenderResult

    /**
     * Detection only, for live preview frames (specs/003). Never throws except for
     * cancellation; an internal failure reports [Confidence.NotFound].
     */
    public suspend fun detect(frame: PreviewFrame): DetectResult

    /** Capability the engine uses unless overridden per call. */
    public val capability: DeviceCapability
}

public sealed interface AnalyzeResult {
    /** `analysis.outcome` is [Outcome.Success] or [Outcome.TooDark]. */
    public data class Ready(
        val analysis: ScanAnalysis,
    ) : AnalyzeResult

    public data class Failed(
        val outcome: Outcome,
    ) : AnalyzeResult
}

public sealed interface RenderResult {
    public data class Ready(
        val output: ScanOutput,
    ) : RenderResult

    public data class Failed(
        val outcome: Outcome,
    ) : RenderResult
}

/** Result of [ScanEngine.analyze]; holds the native session until [close]. */
@Suppress("LongParameterList") // value holder built once from the native info
public class ScanAnalysis internal constructor(
    internal val session: NativeSession,
    public val corners: Quad,
    public val cornerSource: CornerSource,
    public val confidence: Confidence,
    public val rotationApplied: Int,
    public val orientationConfidence: OrientationConfidence,
    public val detectedAspect: Double,
    public val capabilityUsed: DeviceCapability,
    public val workingLongEdgeUsed: Int,
    public val flags: Set<AnalysisFlag>,
    public val steps: List<StepTiming>,
    public val peakNativeBytes: Long,
    public val outcome: Outcome,
) : AutoCloseable {
    public val isLandscape: Boolean
        get() = detectedAspect > 1.0

    public var isClosed: Boolean = false
        private set

    /** Releases the native page. Idempotent. */
    override fun close() {
        if (!isClosed) {
            isClosed = true
            session.destroy()
        }
    }
}
