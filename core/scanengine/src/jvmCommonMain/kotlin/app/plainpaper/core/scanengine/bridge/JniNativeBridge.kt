@file:Suppress("MagicNumber") // wire format / pixel layout constants are the contract, not tunables

package app.plainpaper.core.scanengine.bridge

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.scanengine.ScanPhoto
import app.plainpaper.core.scanengine.model.Confidence
import app.plainpaper.core.scanengine.model.CornerSource
import app.plainpaper.core.scanengine.model.FallbackReason
import app.plainpaper.core.scanengine.model.FitMode
import app.plainpaper.core.scanengine.model.OrientationConfidence
import app.plainpaper.core.scanengine.model.ProcessingStep
import app.plainpaper.core.scanengine.model.Quad
import app.plainpaper.core.scanengine.model.SkipReason
import app.plainpaper.core.scanengine.model.StepStatus
import app.plainpaper.core.scanengine.model.StepTiming

private const val INFO_HEADER = 7
private val INFO_LONGS = INFO_HEADER + 3 * ProcessingStep.entries.size
private const val INFO_DOUBLES = 9
private const val RENDER_INFO = 6
private const val FLAG_INVERTED = 1L
private const val FLAG_SPECULAR = 2L
private const val STEP_STATUS_FAILED = 6L

/** Decoded `outInfo` of a render call. */
public data class RenderMeta(
    val status: NativeStatus,
    val channels: Int,
    val styleUsed: OutputStyle,
    val fallbackReason: FallbackReason?,
    val steps: List<StepTiming>,
) {
    public companion object {
        public fun newBuffer(): LongArray = LongArray(RENDER_INFO)

        public fun decode(outInfo: LongArray): RenderMeta =
            RenderMeta(
                status = NativeStatus.fromCode(outInfo[0].toInt()),
                channels = outInfo[1].toInt(),
                styleUsed = OutputStyle.entries.getOrElse(outInfo[2].toInt()) { OutputStyle.Scan },
                fallbackReason = if (outInfo[3] == 1L) FallbackReason.InvertedDocument else null,
                steps =
                    listOf(
                        StepTiming(ProcessingStep.Render, StepStatus.Applied, outInfo[4]),
                        StepTiming(ProcessingStep.Resample, StepStatus.Applied, outInfo[5]),
                    ),
            )
    }
}

/** Shared JNI implementation; platforms supply the pixel transport (Bitmap vs byte arrays). */
public abstract class JniNativeBridge : NativeBridge {
    private val engine: Long by lazy {
        loadNativeLibrary()
        JniScanBridge.nativeEngineCreate()
    }

    override fun apiVersion(): Int {
        loadNativeLibrary()
        return JniScanBridge.nativeApiVersion()
    }

    override fun createCancelFlag(): NativeCancelFlag {
        loadNativeLibrary()
        return JniCancelFlag(JniScanBridge.nativeCancelFlagCreate())
    }

    override fun analyze(
        photo: ScanPhoto,
        capability: DeviceCapability,
        workingLongEdge: Int,
        allowFlatten: Boolean,
        userCorners: Quad?,
        cancel: NativeCancelFlag,
    ): NativeAnalyzeResult {
        val status = IntArray(1)
        val handle =
            analyzeNative(
                engine = engine,
                photo = photo,
                capability = capability.code,
                workingLongEdge = workingLongEdge,
                allowFlatten = allowFlatten,
                userCorners = userCorners?.toFloatArray(),
                cancel = (cancel as JniCancelFlag).handle,
                outStatus = status,
            )
        val nativeStatus = NativeStatus.fromCode(status[0])
        val carriesSession = nativeStatus == NativeStatus.Ok || nativeStatus == NativeStatus.TooDark
        return if (handle != 0L && carriesSession) {
            NativeAnalyzeResult.Session(JniSession(handle), nativeStatus)
        } else {
            if (handle != 0L) JniScanBridge.nativeSessionDestroy(handle)
            NativeAnalyzeResult.Failure(nativeStatus, JniScanBridge.nativeLastError())
        }
    }

    @Suppress("LongParameterList")
    protected abstract fun analyzeNative(
        engine: Long,
        photo: ScanPhoto,
        capability: Int,
        workingLongEdge: Int,
        allowFlatten: Boolean,
        userCorners: FloatArray?,
        cancel: Long,
        outStatus: IntArray,
    ): Long

    @Suppress("LongParameterList")
    protected abstract fun renderNative(
        session: Long,
        style: Int,
        fit: Int,
        width: Int,
        height: Int,
        cancel: Long,
    ): NativeRenderResult

    private inner class JniSession(
        private val handle: Long,
    ) : NativeSession {
        override fun info(): NativeAnalysisInfo {
            val longs = LongArray(INFO_LONGS)
            val doubles = DoubleArray(INFO_DOUBLES)
            JniScanBridge.nativeSessionInfo(handle, longs, doubles)
            return decodeInfo(longs, doubles)
        }

        override fun render(
            style: OutputStyle,
            fit: FitMode,
            width: Int,
            height: Int,
            cancel: NativeCancelFlag,
        ): NativeRenderResult =
            renderNative(
                session = handle,
                style = style.ordinal,
                fit = fit.ordinal,
                width = width,
                height = height,
                cancel = (cancel as JniCancelFlag).handle,
            )

        override fun destroy() {
            JniScanBridge.nativeSessionDestroy(handle)
        }
    }

    private class JniCancelFlag(
        val handle: Long,
    ) : NativeCancelFlag {
        override fun set() {
            JniScanBridge.nativeCancelFlagSet(handle)
        }

        override fun destroy() {
            JniScanBridge.nativeCancelFlagDestroy(handle)
        }
    }

    public companion object {
        private val DeviceCapability.code: Int
            get() = if (this == DeviceCapability.Full) 0 else 1

        /** Unpacks the arrays filled by `nativeSessionInfo` (see plainpaper_jni.cpp). */
        public fun decodeInfo(
            longs: LongArray,
            doubles: DoubleArray,
        ): NativeAnalysisInfo {
            val stepCount = longs[5].toInt().coerceIn(0, ProcessingStep.entries.size)
            val steps =
                List(stepCount) { i ->
                    val base = INFO_HEADER + i * 3
                    StepTiming(
                        step = ProcessingStep.entries[longs[base].toInt().coerceIn(0, ProcessingStep.entries.size - 1)],
                        status = decodeStepStatus(longs[base + 1]),
                        durationMicros = longs[base + 2],
                    )
                }
            return NativeAnalysisInfo(
                corners = Quad.fromDoubles(doubles),
                cornerSource = CornerSource.entries[longs[0].toInt().coerceIn(0, 2)],
                confidence = Confidence.entries[longs[1].toInt().coerceIn(0, 2)],
                rotationApplied = longs[2].toInt(),
                orientationConfidence = OrientationConfidence.entries[longs[3].toInt().coerceIn(0, 2)],
                detectedAspect = doubles[8],
                invertedDocument = longs[4] and FLAG_INVERTED != 0L,
                specularHighlight = longs[4] and FLAG_SPECULAR != 0L,
                steps = steps,
                peakBytes = longs[6],
            )
        }

        private fun decodeStepStatus(code: Long): StepStatus =
            when {
                code == 0L -> {
                    StepStatus.Applied
                }

                code >= STEP_STATUS_FAILED -> {
                    StepStatus.Failed("native")
                }

                else -> {
                    StepStatus.Skipped(
                        SkipReason.entries[(code - 1).toInt().coerceIn(0, SkipReason.entries.size - 1)],
                    )
                }
            }
    }
}
