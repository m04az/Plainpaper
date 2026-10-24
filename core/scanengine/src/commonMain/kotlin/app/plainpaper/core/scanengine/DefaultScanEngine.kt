package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.bridge.NativeAnalyzeResult
import app.plainpaper.core.scanengine.bridge.NativeBridge
import app.plainpaper.core.scanengine.bridge.NativeCancelFlag
import app.plainpaper.core.scanengine.bridge.NativeDetectResult
import app.plainpaper.core.scanengine.bridge.NativeRenderResult
import app.plainpaper.core.scanengine.bridge.NativeStatus
import app.plainpaper.core.scanengine.decision.ExecutionPlan
import app.plainpaper.core.scanengine.decision.PageFraming
import app.plainpaper.core.scanengine.decision.RetryPolicy
import app.plainpaper.core.scanengine.decision.StyleFallback
import app.plainpaper.core.scanengine.decision.UserCornersValidation
import app.plainpaper.core.scanengine.decision.WorkingResolution
import app.plainpaper.core.scanengine.model.AnalysisFlag
import app.plainpaper.core.scanengine.model.AnalyzeOptions
import app.plainpaper.core.scanengine.model.Confidence
import app.plainpaper.core.scanengine.model.DetectResult
import app.plainpaper.core.scanengine.model.Outcome
import app.plainpaper.core.scanengine.model.RenderOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine façade over the native bridge: dispatching, cooperative cancellation, the
 * out-of-memory retry and status mapping (contracts/kotlin-api.md K1-K6, K10).
 */
public class DefaultScanEngine(
    private val bridge: NativeBridge,
    private val capabilityProvider: CapabilityProvider,
    private val dispatchers: DispatcherProvider,
) : ScanEngine {
    override val capability: DeviceCapability
        get() = capabilityProvider.current()

    override suspend fun analyze(
        photo: ScanPhoto,
        options: AnalyzeOptions,
    ): AnalyzeResult =
        withContext(dispatchers.default) {
            if (bridge.apiVersion() != NativeBridge.API_VERSION) {
                return@withContext AnalyzeResult.Failed(Outcome.Internal(API_VERSION_MISMATCH))
            }
            options.userCorners?.let { corners ->
                UserCornersValidation.validate(corners, photo.width, photo.height)?.let { detail ->
                    return@withContext AnalyzeResult.Failed(Outcome.InvalidInput(detail))
                }
            }
            val initialCapability = options.capability ?: capability
            val workingLongEdge = WorkingResolution.resolve(initialCapability, options.workingLongEdge)
            analyzeWithRetry(photo, options, RetryPolicy.initial(initialCapability, workingLongEdge))
        }

    private suspend fun analyzeWithRetry(
        photo: ScanPhoto,
        options: AnalyzeOptions,
        initial: ExecutionPlan,
    ): AnalyzeResult {
        var plan = initial
        while (true) {
            val result = withCancelFlag { flag -> runAnalysis(photo, options, plan, flag) }
            val outOfMemory = (result as? AnalyzeResult.Failed)?.outcome == Outcome.OutOfMemory
            val next = if (outOfMemory) RetryPolicy.afterOutOfMemory(plan) else null
            if (next == null) return result
            plan = next
        }
    }

    private fun runAnalysis(
        photo: ScanPhoto,
        options: AnalyzeOptions,
        plan: ExecutionPlan,
        flag: NativeCancelFlag,
    ): AnalyzeResult =
        when (
            val native =
                bridge.analyze(
                    photo = photo,
                    capability = plan.capability,
                    workingLongEdge = plan.workingLongEdge,
                    allowFlatten = options.allowFlatten && plan.capability == DeviceCapability.Full,
                    userCorners = options.userCorners,
                    cancel = flag,
                )
        ) {
            is NativeAnalyzeResult.Failure -> {
                AnalyzeResult.Failed(native.status.toOutcome(native.detail))
            }

            is NativeAnalyzeResult.Session -> {
                val info = native.session.info()
                val flags =
                    buildSet {
                        if (info.invertedDocument) add(AnalysisFlag.InvertedDocument)
                        if (info.specularHighlight) add(AnalysisFlag.SpecularHighlight)
                        if (plan.attempt > 0) add(AnalysisFlag.RetriedAtLowerResolution)
                    }
                AnalyzeResult.Ready(
                    ScanAnalysis(
                        session = native.session,
                        corners = info.corners,
                        cornerSource = info.cornerSource,
                        confidence = info.confidence,
                        rotationApplied = info.rotationApplied,
                        orientationConfidence = info.orientationConfidence,
                        detectedAspect = info.detectedAspect,
                        capabilityUsed = plan.capability,
                        workingLongEdgeUsed = plan.workingLongEdge,
                        flags = flags,
                        steps = info.steps,
                        peakNativeBytes = info.peakBytes,
                        outcome = if (native.status == NativeStatus.TooDark) Outcome.TooDark else Outcome.Success,
                    ),
                )
            }
        }

    override suspend fun detect(frame: PreviewFrame): DetectResult =
        withContext(dispatchers.default) {
            withCancelFlag { flag -> bridge.detect(frame, flag) }.toDetectResult()
        }

    private fun NativeDetectResult.toDetectResult(): DetectResult =
        when (status) {
            NativeStatus.Ok -> {
                DetectResult(
                    corners = corners.takeIf { confidence != Confidence.NotFound },
                    confidence = confidence,
                    fillFraction = fillFraction,
                    meanLuma = meanLuma,
                    micros = micros,
                )
            }

            NativeStatus.Cancelled -> {
                throw CancellationException("native cancelled")
            }

            // Preview frames are best effort: any failure reads as "no page" (contracts/kotlin-api.md).
            else -> {
                DetectResult.notFound(meanLuma, micros)
            }
        }

    override suspend fun render(
        analysis: ScanAnalysis,
        options: RenderOptions,
    ): RenderResult =
        withContext(dispatchers.default) {
            check(!analysis.isClosed) { "ScanAnalysis is closed" }
            val size = PageFraming.outputSize(options.pageSize, analysis.detectedAspect, analysis.isLandscape)
            val style = StyleFallback.resolve(options.style, analysis.flags).style
            withCancelFlag { flag ->
                when (val native = analysis.session.render(style, options.fit, size.width, size.height, flag)) {
                    is NativeRenderResult.Ready -> RenderResult.Ready(native.output)
                    is NativeRenderResult.Failure -> RenderResult.Failed(native.status.toOutcome(native.detail))
                }
            }
        }

    /**
     * Runs a blocking native call with a cancel flag that is set synchronously when the calling
     * coroutine is cancelled (`invokeOnCancellation` handlers run on the cancelling thread); a
     * resume after cancellation is ignored, so the caller only ever sees CancellationException.
     * A `Cancelled` native status is also turned into coroutine cancellation.
     */
    private suspend fun <T> withCancelFlag(block: (NativeCancelFlag) -> T): T {
        val flag = bridge.createCancelFlag()
        try {
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { flag.set() }
                val result = runCatching { block(flag) }
                result.exceptionOrNull()?.let { error ->
                    if (error is CancellationException && !continuation.isActive) return@suspendCancellableCoroutine
                    continuation.resumeWithException(error)
                    return@suspendCancellableCoroutine
                }
                continuation.resume(result.getOrThrow())
            }
        } finally {
            flag.destroy()
        }
    }

    private fun NativeStatus.toOutcome(detail: String): Outcome =
        when (this) {
            NativeStatus.Ok -> Outcome.Success
            NativeStatus.Cancelled -> throw CancellationException("native cancelled")
            NativeStatus.InvalidInput -> Outcome.InvalidInput(detail)
            NativeStatus.TooDark -> Outcome.TooDark
            NativeStatus.OutOfMemory -> Outcome.OutOfMemory
            NativeStatus.Internal -> Outcome.Internal(detail.ifEmpty { "native" })
        }

    public companion object {
        public const val API_VERSION_MISMATCH: String = "api-version"
    }
}
