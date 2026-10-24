@file:Suppress("MagicNumber") // wire format / pixel layout constants are the contract, not tunables

package app.plainpaper.core.scanengine.bridge

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.scanengine.PreviewFrame
import app.plainpaper.core.scanengine.ScanOutput
import app.plainpaper.core.scanengine.ScanPhoto
import app.plainpaper.core.scanengine.model.Confidence
import app.plainpaper.core.scanengine.model.CornerSource
import app.plainpaper.core.scanengine.model.FitMode
import app.plainpaper.core.scanengine.model.OrientationConfidence
import app.plainpaper.core.scanengine.model.Quad
import app.plainpaper.core.scanengine.model.StepTiming

/** Mirrors `pp_status` (contracts/c-api.md). */
public enum class NativeStatus(
    public val code: Int,
) {
    Ok(0),
    Cancelled(1),
    InvalidInput(2),
    TooDark(3),
    OutOfMemory(4),
    Internal(5),
    ;

    public companion object {
        public fun fromCode(code: Int): NativeStatus = entries.firstOrNull { it.code == code } ?: Internal
    }
}

/** `pp_analysis_info` decoded from the native session. */
public data class NativeAnalysisInfo(
    val corners: Quad,
    val cornerSource: CornerSource,
    val confidence: Confidence,
    val rotationApplied: Int,
    val orientationConfidence: OrientationConfidence,
    val detectedAspect: Double,
    val invertedDocument: Boolean,
    val specularHighlight: Boolean,
    val steps: List<StepTiming>,
    val peakBytes: Long,
)

public sealed interface NativeAnalyzeResult {
    /** [status] is [NativeStatus.Ok] or [NativeStatus.TooDark]; both carry a session. */
    public data class Session(
        val session: NativeSession,
        val status: NativeStatus,
    ) : NativeAnalyzeResult

    public data class Failure(
        val status: NativeStatus,
        val detail: String,
    ) : NativeAnalyzeResult
}

public sealed interface NativeRenderResult {
    public data class Ready(
        val output: ScanOutput,
    ) : NativeRenderResult

    public data class Failure(
        val status: NativeStatus,
        val detail: String,
    ) : NativeRenderResult
}

/** `pp_detect_info` plus the call status (contracts/c-api-detect.md). */
public data class NativeDetectResult(
    val status: NativeStatus,
    val corners: Quad,
    val confidence: Confidence,
    val fillFraction: Float,
    val meanLuma: Float,
    val micros: Long,
    val detail: String = "",
) {
    public companion object {
        /** Unpacks the 12 doubles written by the JNI/cinterop transport. */
        public fun decode(
            status: NativeStatus,
            values: DoubleArray,
            detail: String = "",
        ): NativeDetectResult =
            NativeDetectResult(
                status = status,
                corners = Quad.fromDoubles(values),
                confidence = Confidence.entries[values[8].toInt().coerceIn(0, 2)],
                meanLuma = values[9].toFloat(),
                fillFraction = values[10].toFloat(),
                micros = values[11].toLong(),
                detail = detail,
            )

        public const val VALUES: Int = 12
    }
}

/** Cooperative cancellation handle shared with native code. */
public interface NativeCancelFlag {
    public fun set()

    public fun destroy()
}

/** One `pp_session`. */
public interface NativeSession {
    public fun info(): NativeAnalysisInfo

    public fun render(
        style: OutputStyle,
        fit: FitMode,
        width: Int,
        height: Int,
        cancel: NativeCancelFlag,
    ): NativeRenderResult

    public fun destroy()
}

/** Thin, blocking wrapper over the C API; one implementation per platform. */
public interface NativeBridge {
    public fun apiVersion(): Int

    public fun createCancelFlag(): NativeCancelFlag

    public fun analyze(
        photo: ScanPhoto,
        capability: DeviceCapability,
        workingLongEdge: Int,
        allowFlatten: Boolean,
        userCorners: Quad?,
        cancel: NativeCancelFlag,
    ): NativeAnalyzeResult

    /** Detection only on a preview frame; blocking. */
    public fun detect(
        frame: PreviewFrame,
        cancel: NativeCancelFlag,
    ): NativeDetectResult

    public companion object {
        /** Must equal `PP_API_VERSION` in plainpaper_scan.h. */
        public const val API_VERSION: Int = 1
    }
}

/** Platform factory used by the Koin module. */
public expect fun platformNativeBridge(): NativeBridge
