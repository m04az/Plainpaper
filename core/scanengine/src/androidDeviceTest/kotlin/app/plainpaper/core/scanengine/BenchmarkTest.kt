package app.plainpaper.core.scanengine

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.scanengine.bridge.platformNativeBridge
import app.plainpaper.core.scanengine.model.RenderOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device benchmark (research R16, SC-005/SC-006): processes every photo in the shared
 * `Download/plainpaper-bench/` folder (pushed by scripts/bench-android.sh, which also grants the
 * media-read permission), `iterations` times, in the Scan style at A4, and writes `bench.json`
 * into the app's external files directory with per-run timings and the native heap delta
 * measured one second after each run. Fails on any non-success outcome.
 */
@RunWith(AndroidJUnit4::class)
class BenchmarkTest {
    private class DeviceDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    @Test
    fun benchmarkPushedPhotos() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val arguments = InstrumentationRegistry.getArguments()
            val iterations = arguments.getString("iterations")?.toIntOrNull() ?: 2
            val dir =
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "plainpaper-bench")
            val outputDir =
                requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)) { "external files dir" }
            val photos =
                dir
                    .listFiles { f ->
                        f.extension.lowercase() in setOf("jpg", "jpeg", "png")
                    }?.sortedBy { it.name }
                    .orEmpty()
            assertTrue("no photos in $dir (run scripts/bench-android.sh)", photos.isNotEmpty())
            val context = instrumentation.targetContext
            val capability = AndroidCapabilityProvider(context)
            val engine = DefaultScanEngine(platformNativeBridge(), capability, DeviceDispatchers())
            val runs = JSONArray()
            var failures = 0
            repeat(iterations) { iteration ->
                for (file in photos) {
                    val baseline = Debug.getNativeHeapAllocatedSize()
                    val bitmap = BitmapFactory.decodeFile(file.path)
                    val start = SystemClock.elapsedRealtimeNanos()
                    val result = engine.analyze(BitmapScanPhoto(bitmap))
                    val analyzeMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                    val run =
                        JSONObject()
                            .put(
                                "photo",
                                file.name,
                            ).put("iteration", iteration)
                            .put("analyzeMs", analyzeMs)
                    if (result is AnalyzeResult.Ready) {
                        val analysis = result.analysis
                        val renderStart = SystemClock.elapsedRealtimeNanos()
                        val rendered = engine.render(analysis, RenderOptions(OutputStyle.Scan, PageSize.A4))
                        run.put("renderMs", (SystemClock.elapsedRealtimeNanos() - renderStart) / 1_000_000.0)
                        run.put("totalMs", (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
                        run.put("outcome", analysis.outcome.toString())
                        run.put("confidence", analysis.confidence.name)
                        run.put("peakNativeBytes", analysis.peakNativeBytes)
                        run.put(
                            "steps",
                            JSONArray(analysis.steps.map { "${it.step}:${it.status}:${it.durationMicros}" }),
                        )
                        if (rendered !is RenderResult.Ready) failures++
                        analysis.close()
                    } else {
                        run.put("outcome", (result as AnalyzeResult.Failed).outcome.toString())
                        failures++
                    }
                    bitmap.recycle()
                    System.gc()
                    SystemClock.sleep(1_000)
                    run.put("nativeHeapDeltaBytes", Debug.getNativeHeapAllocatedSize() - baseline)
                    runs.put(run)
                }
            }
            val details = capability.details()
            val report =
                JSONObject()
                    .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                    .put("androidSdk", Build.VERSION.SDK_INT)
                    .put("build", Build.DISPLAY)
                    .put("capability", capability.current().name)
                    .put("totalMemoryBytes", details.totalMemoryBytes)
                    .put("iterations", iterations)
                    .put("runs", runs)
            File(outputDir, "bench.json").writeText(report.toString(2))
            assertEquals("failed runs", 0, failures)
        }
}
