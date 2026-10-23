package app.plainpaper.core.scanengine

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.scanengine.bridge.platformNativeBridge
import app.plainpaper.core.scanengine.model.Confidence
import app.plainpaper.core.scanengine.model.RenderOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real native engine on the device/emulator with a bundled synthetic page (T062). */
@RunWith(AndroidJUnit4::class)
class SmokeDeviceTest {
    private class DeviceDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    @Test
    fun analyzesAndRendersTheSamplePage() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().context
            val bitmap = context.assets.open("sample-page.jpg").use { BitmapFactory.decodeStream(it) }
            val engine =
                DefaultScanEngine(platformNativeBridge(), AndroidCapabilityProvider(context), DeviceDispatchers())
            val result = engine.analyze(BitmapScanPhoto(bitmap))
            assertTrue("analyze: $result", result is AnalyzeResult.Ready)
            val analysis = (result as AnalyzeResult.Ready).analysis
            analysis.use {
                assertEquals(Confidence.High, analysis.confidence)
                assertEquals(0, analysis.rotationApplied)
                val rendered = engine.render(analysis, RenderOptions(OutputStyle.Scan, PageSize.A4))
                assertTrue("render: $rendered", rendered is RenderResult.Ready)
                val output = (rendered as RenderResult.Ready).output
                assertEquals(2480, output.width)
                assertEquals(3508, output.height)
                assertEquals(1, output.channels)
                assertTrue(analysis.steps.isNotEmpty())
            }
        }
}
