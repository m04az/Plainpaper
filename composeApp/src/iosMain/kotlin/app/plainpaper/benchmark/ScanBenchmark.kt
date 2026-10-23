package app.plainpaper.benchmark

import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.DefaultScanEngine
import app.plainpaper.core.scanengine.IosCapabilityProvider
import app.plainpaper.core.scanengine.IosPhotoDecoder
import app.plainpaper.core.scanengine.RenderResult
import app.plainpaper.core.scanengine.bridge.platformNativeBridge
import app.plainpaper.core.scanengine.model.RenderOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.time.TimeSource

/**
 * Device benchmark entry for the iOS XCTest target (research R16): processes the given photo
 * paths `iterations` times in the Scan style at A4 and returns a JSON report. Mirrors the
 * Android `BenchmarkTest`; results are captured manually on the owner's iPhone.
 */
object ScanBenchmark {
    private class IosDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    fun run(
        paths: List<String>,
        iterations: Int,
    ): String =
        runBlocking {
            val capability = IosCapabilityProvider()
            val dispatchers = IosDispatchers()
            val engine = DefaultScanEngine(platformNativeBridge(), capability, dispatchers)
            val decoder = IosPhotoDecoder(dispatchers)
            val runs = StringBuilder()
            var failures = 0
            repeat(iterations) { iteration ->
                for (path in paths) {
                    val photo = (decoder.decode(path) as? DecodeResult.Ready)?.photo
                    if (photo == null) {
                        failures++
                        continue
                    }
                    val start = TimeSource.Monotonic.markNow()
                    val result = engine.analyze(photo)
                    val analyzeMs = start.elapsedNow().inWholeMicroseconds / 1000.0
                    val entry =
                        StringBuilder(
                            "{\"photo\":\"${path.substringAfterLast(
                                '/',
                            )}\",\"iteration\":$iteration,\"analyzeMs\":$analyzeMs",
                        )
                    if (result is AnalyzeResult.Ready) {
                        val rendered = engine.render(result.analysis, RenderOptions(OutputStyle.Scan, PageSize.A4))
                        entry.append(",\"totalMs\":${start.elapsedNow().inWholeMicroseconds / 1000.0}")
                        entry.append(
                            ",\"outcome\":\"${result.analysis.outcome}\",\"confidence\":\"${result.analysis.confidence}\"",
                        )
                        entry.append(",\"peakNativeBytes\":${result.analysis.peakNativeBytes}")
                        if (rendered !is RenderResult.Ready) failures++
                        result.analysis.close()
                    } else {
                        entry.append(",\"outcome\":\"${(result as AnalyzeResult.Failed).outcome}\"")
                        failures++
                    }
                    entry.append("}")
                    if (runs.isNotEmpty()) runs.append(",")
                    runs.append(entry)
                }
            }
            val details = capability.details()
            "{\"device\":\"${details.platformName}\",\"capability\":\"${capability.current()}\"," +
                "\"totalMemoryBytes\":${details.totalMemoryBytes},\"iterations\":$iterations," +
                "\"failures\":$failures,\"runs\":[$runs]}"
        }
}
