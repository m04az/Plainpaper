package app.plainpaper.core.scanengine.model

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize

/** Moved to `:core:domain` (003) so review and capture can share them; kept here as aliases. */
public typealias Point = app.plainpaper.core.domain.model.Point

public typealias Quad = app.plainpaper.core.domain.model.Quad

public typealias Confidence = app.plainpaper.core.domain.model.Confidence

/** Options of [app.plainpaper.core.scanengine.ScanEngine.analyze] (data-model.md). */
public data class AnalyzeOptions(
    /** When set, detection is skipped and these corners are used (`cornerSource = User`). */
    val userCorners: Quad? = null,
    /** Override of the device capability (benchmarks, diagnostics). */
    val capability: DeviceCapability? = null,
    /** Override of the working resolution long edge, 800..3000. */
    val workingLongEdge: Int? = null,
    /** Allows curl flattening on Full capability. */
    val allowFlatten: Boolean = true,
)

public enum class CornerSource { Detected, User, FullFrame }

public enum class OrientationConfidence { High, Low, None }

/** Pipeline stages in execution order (FR-019). */
public enum class ProcessingStep {
    Decode,
    Detect,
    Perspective,
    Deskew,
    Orient,
    Flatten,
    Illumination,
    Render,
    Resample,
}

public enum class SkipReason { Capability, InsufficientLines, NoImprovement, UserCorners, NotNeeded }

public sealed interface StepStatus {
    public data object Applied : StepStatus

    public data class Skipped(
        val reason: SkipReason,
    ) : StepStatus

    public data class Failed(
        val reason: String,
    ) : StepStatus
}

public data class StepTiming(
    val step: ProcessingStep,
    val status: StepStatus,
    val durationMicros: Long,
)

public enum class AnalysisFlag { InvertedDocument, SpecularHighlight, RetriedAtLowerResolution }

/** Result classification of an engine call; exceptions never escape the engine (FR-014). */
public sealed interface Outcome {
    public data object Success : Outcome

    public data object Cancelled : Outcome

    public data class InvalidInput(
        val detail: String,
    ) : Outcome

    public data object TooDark : Outcome

    public data object OutOfMemory : Outcome

    public data class Internal(
        val code: String,
    ) : Outcome
}

/** How the corrected page is placed in the output frame. */
public enum class FitMode {
    /** Centred on white margins, aspect preserved. */
    Fit,

    /** Output keeps the page proportions (only with [PageSize.Natural]). */
    Natural,
}

public data class RenderOptions(
    val style: OutputStyle,
    val pageSize: PageSize,
    val fit: FitMode = if (pageSize == PageSize.Natural) FitMode.Natural else FitMode.Fit,
)

public enum class FallbackReason { InvertedDocument }

public data class CapabilityDetails(
    val totalMemoryBytes: Long,
    val is64Bit: Boolean,
    val isLowRamDevice: Boolean,
    val platformName: String,
)

/** Diagnostics for FR-019, device benchmarks and the quality suite. */
public data class ScanReport(
    val corners: Quad,
    val cornerSource: CornerSource,
    val confidence: Confidence,
    val rotationApplied: Int,
    val capabilityUsed: DeviceCapability,
    val workingLongEdgeUsed: Int,
    val flags: Set<AnalysisFlag>,
    val steps: List<StepTiming>,
    val totalMicros: Long,
    val peakNativeBytes: Long,
)
