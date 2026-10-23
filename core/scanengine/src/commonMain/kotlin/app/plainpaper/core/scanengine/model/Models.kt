package app.plainpaper.core.scanengine.model

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import kotlin.math.abs

/** A point in input-photo pixels (origin top-left, x right, y down). */
public data class Point(
    val x: Float,
    val y: Float,
)

/** Four page corners in input-photo pixels, clockwise from the top-left. */
public data class Quad(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point,
) {
    public val points: List<Point>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Shoelace area in square pixels. */
    public val area: Double
        get() {
            var sum = 0.0
            val pts = points
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                sum += a.x.toDouble() * b.y - b.x.toDouble() * a.y
            }
            return abs(sum) / 2.0
        }

    /** True when every turn has the same sign (no self-intersection, no collinear triple). */
    public val isConvex: Boolean
        get() {
            val pts = points
            var sign = 0
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                val c = pts[(i + 2) % pts.size]
                val cross = (b.x - a.x).toDouble() * (c.y - b.y) - (b.y - a.y).toDouble() * (c.x - b.x)
                val s =
                    if (cross > 0) {
                        1
                    } else if (cross < 0) {
                        -1
                    } else {
                        0
                    }
                if (s == 0) return false
                if (sign == 0) {
                    sign = s
                } else if (s != sign) {
                    return false
                }
            }
            return true
        }

    /** `[x0, y0, x1, y1, x2, y2, x3, y3]` in TL, TR, BR, BL order (bridge wire format). */
    public fun toFloatArray(): FloatArray =
        floatArrayOf(
            topLeft.x,
            topLeft.y,
            topRight.x,
            topRight.y,
            bottomRight.x,
            bottomRight.y,
            bottomLeft.x,
            bottomLeft.y,
        )

    public companion object {
        public fun fullFrame(
            width: Int,
            height: Int,
        ): Quad {
            val w = width.toFloat()
            val h = height.toFloat()
            return Quad(Point(0f, 0f), Point(w, 0f), Point(w, h), Point(0f, h))
        }

        @Suppress("MagicNumber") // wire format: 8 coordinates in TL, TR, BR, BL order
        public fun fromDoubles(values: DoubleArray): Quad =
            Quad(
                Point(values[0].toFloat(), values[1].toFloat()),
                Point(values[2].toFloat(), values[3].toFloat()),
                Point(values[4].toFloat(), values[5].toFloat()),
                Point(values[6].toFloat(), values[7].toFloat()),
            )
    }
}

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

public enum class Confidence { High, Low, NotFound }

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
