@file:Suppress("MagicNumber") // wire format / pixel layout constants are the contract, not tunables

package app.plainpaper.core.testing

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.scanengine.PreviewFrame
import app.plainpaper.core.scanengine.ScanPhoto
import app.plainpaper.core.scanengine.bridge.NativeAnalysisInfo
import app.plainpaper.core.scanengine.bridge.NativeAnalyzeResult
import app.plainpaper.core.scanengine.bridge.NativeBridge
import app.plainpaper.core.scanengine.bridge.NativeCancelFlag
import app.plainpaper.core.scanengine.bridge.NativeDetectResult
import app.plainpaper.core.scanengine.bridge.NativeRenderResult
import app.plainpaper.core.scanengine.bridge.NativeSession
import app.plainpaper.core.scanengine.bridge.NativeStatus
import app.plainpaper.core.scanengine.model.Confidence
import app.plainpaper.core.scanengine.model.CornerSource
import app.plainpaper.core.scanengine.model.DetectorKind
import app.plainpaper.core.scanengine.model.FitMode
import app.plainpaper.core.scanengine.model.OrientationConfidence
import app.plainpaper.core.scanengine.model.ProcessingStep
import app.plainpaper.core.scanengine.model.Quad
import app.plainpaper.core.scanengine.model.StepStatus
import app.plainpaper.core.scanengine.model.StepTiming

/** Scripted [NativeBridge]: queue statuses, inspect calls, simulate cancellation. */
class FakeNativeBridge(
    var apiVersion: Int = NativeBridge.API_VERSION,
) : NativeBridge {
    data class AnalyzeCall(
        val photo: ScanPhoto,
        val capability: DeviceCapability,
        val workingLongEdge: Int,
        val allowFlatten: Boolean,
        val keepOrientation: Boolean,
        val userCorners: Quad?,
        val flagSetDuringCall: Boolean,
    )

    sealed interface Script {
        data class Fail(
            val status: NativeStatus,
            val detail: String = "",
        ) : Script

        data class Succeed(
            val status: NativeStatus = NativeStatus.Ok,
            val info: NativeAnalysisInfo = defaultInfo(),
        ) : Script
    }

    class FakeCancelFlag : NativeCancelFlag {
        var isSet: Boolean = false
            private set
        var destroyed: Boolean = false
            private set

        override fun set() {
            isSet = true
        }

        override fun destroy() {
            destroyed = true
        }
    }

    inner class FakeSession(
        private val info: NativeAnalysisInfo,
    ) : NativeSession {
        var destroyCount: Int = 0
            private set
        val renders = mutableListOf<Triple<OutputStyle, FitMode, Pair<Int, Int>>>()

        override fun info(): NativeAnalysisInfo = info

        override fun render(
            style: OutputStyle,
            fit: FitMode,
            width: Int,
            height: Int,
            cancel: NativeCancelFlag,
        ): NativeRenderResult {
            renders += Triple(style, fit, width to height)
            onRender?.invoke()
            if ((cancel as FakeCancelFlag).isSet) return NativeRenderResult.Failure(NativeStatus.Cancelled, "")
            renderScript.removeFirstOrNull()?.let { return NativeRenderResult.Failure(it.status, it.detail) }
            return NativeRenderResult.Ready(FakeScanOutput(width, height, style))
        }

        override fun destroy() {
            destroyCount++
        }
    }

    val analyzeScript = ArrayDeque<Script>()
    val detectScript = ArrayDeque<NativeDetectResult>()
    val detectCalls = mutableListOf<PreviewFrame>()
    val renderScript = ArrayDeque<Script.Fail>()
    val calls = mutableListOf<AnalyzeCall>()
    val flags = mutableListOf<FakeCancelFlag>()
    val sessions = mutableListOf<FakeSession>()

    /** Invoked inside [analyze] before the cancel flag is checked (simulates work in native code). */
    var onAnalyze: (() -> Unit)? = null
    var onRender: (() -> Unit)? = null

    /** Result of [loadDetector]; set false to simulate unusable model bytes. */
    var loadDetectorResult: Boolean = true
    val loadedDetectors = mutableListOf<ByteArray>()

    /** Every [setDetectorEnabled] call in order. */
    val detectorEnabledCalls = mutableListOf<Boolean>()

    override fun apiVersion(): Int = apiVersion

    override fun createCancelFlag(): NativeCancelFlag = FakeCancelFlag().also { flags += it }

    override fun analyze(
        photo: ScanPhoto,
        capability: DeviceCapability,
        workingLongEdge: Int,
        allowFlatten: Boolean,
        keepOrientation: Boolean,
        userCorners: Quad?,
        cancel: NativeCancelFlag,
    ): NativeAnalyzeResult {
        onAnalyze?.invoke()
        val flag = cancel as FakeCancelFlag
        calls += AnalyzeCall(photo, capability, workingLongEdge, allowFlatten, keepOrientation, userCorners, flag.isSet)
        if (flag.isSet) return NativeAnalyzeResult.Failure(NativeStatus.Cancelled, "")
        return when (val script = analyzeScript.removeFirstOrNull() ?: Script.Succeed()) {
            is Script.Fail -> {
                NativeAnalyzeResult.Failure(script.status, script.detail)
            }

            is Script.Succeed -> {
                NativeAnalyzeResult.Session(
                    FakeSession(script.info).also { sessions += it },
                    script.status,
                )
            }
        }
    }

    override fun detect(
        frame: PreviewFrame,
        cancel: NativeCancelFlag,
    ): NativeDetectResult {
        detectCalls += frame
        if ((cancel as FakeCancelFlag).isSet) return defaultDetect(frame).copy(status = NativeStatus.Cancelled)
        return detectScript.removeFirstOrNull() ?: defaultDetect(frame)
    }

    override fun loadDetector(bytes: ByteArray): Boolean {
        loadedDetectors += bytes
        return loadDetectorResult
    }

    override fun setDetectorEnabled(enabled: Boolean) {
        detectorEnabledCalls += enabled
    }

    companion object {
        fun defaultDetect(
            frame: PreviewFrame,
            confidence: Confidence = Confidence.High,
            detector: DetectorKind = DetectorKind.Classic,
            micros: Long = 5_000,
        ): NativeDetectResult =
            NativeDetectResult(
                status = NativeStatus.Ok,
                corners = Quad.fullFrame(frame.width, frame.height),
                confidence = confidence,
                fillFraction = 1f,
                meanLuma = 128f,
                micros = micros,
                detector = detector,
                presence = if (detector == DetectorKind.Model) 0.95f else 0f,
            )

        fun defaultInfo(
            corners: Quad = Quad.fullFrame(4000, 3000),
            confidence: Confidence = Confidence.High,
            detectedAspect: Double = 0.707,
        ): NativeAnalysisInfo =
            NativeAnalysisInfo(
                corners = corners,
                cornerSource = CornerSource.Detected,
                confidence = confidence,
                rotationApplied = 0,
                orientationConfidence = OrientationConfidence.High,
                detectedAspect = detectedAspect,
                invertedDocument = false,
                specularHighlight = false,
                steps = listOf(StepTiming(ProcessingStep.Detect, StepStatus.Applied, 10_000)),
                peakBytes = 1_000_000,
            )
    }
}
