package app.plainpaper.core.testing

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.PreviewFrame
import app.plainpaper.core.scanengine.RenderResult
import app.plainpaper.core.scanengine.ScanAnalysis
import app.plainpaper.core.scanengine.ScanEngine
import app.plainpaper.core.scanengine.ScanPhoto
import app.plainpaper.core.scanengine.model.AnalyzeOptions
import app.plainpaper.core.scanengine.model.DetectResult
import app.plainpaper.core.scanengine.model.RenderOptions

/** Scripted [ScanEngine] for feature tests (Features 3/4). */
class FakeScanEngine(
    override var capability: DeviceCapability = DeviceCapability.Full,
) : ScanEngine {
    val analyzeCalls = mutableListOf<Pair<ScanPhoto, AnalyzeOptions>>()
    val renderCalls = mutableListOf<Pair<ScanAnalysis, RenderOptions>>()
    var nextAnalyzeResult: (suspend (ScanPhoto, AnalyzeOptions) -> AnalyzeResult)? = null
    var nextRenderResult: (suspend (ScanAnalysis, RenderOptions) -> RenderResult)? = null
    val detectCalls = mutableListOf<PreviewFrame>()

    /** Defaults to "no page" so camera tests only script the frames they care about. */
    var nextDetectResult: (suspend (PreviewFrame) -> DetectResult)? = null

    override suspend fun detect(frame: PreviewFrame): DetectResult {
        detectCalls += frame
        return nextDetectResult?.invoke(frame) ?: DetectResult.notFound()
    }

    override suspend fun analyze(
        photo: ScanPhoto,
        options: AnalyzeOptions,
    ): AnalyzeResult {
        analyzeCalls += photo to options
        return requireNotNull(nextAnalyzeResult) { "FakeScanEngine.nextAnalyzeResult not set" }(photo, options)
    }

    override suspend fun render(
        analysis: ScanAnalysis,
        options: RenderOptions,
    ): RenderResult {
        renderCalls += analysis to options
        return requireNotNull(nextRenderResult) { "FakeScanEngine.nextRenderResult not set" }(analysis, options)
    }
}
