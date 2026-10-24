package app.plainpaper.feature.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.plainpaper.core.capture.background.BackgroundTask
import app.plainpaper.core.capture.files.CopyFailure
import app.plainpaper.core.capture.files.CopyResult
import app.plainpaper.core.capture.files.ImageFiles
import app.plainpaper.core.capture.files.androidImageFiles
import app.plainpaper.core.data.capture.FileCaptureSessionRepository
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.scanengine.AndroidCapabilityProvider
import app.plainpaper.core.scanengine.AndroidPhotoDecoder
import app.plainpaper.core.scanengine.DefaultScanEngine
import app.plainpaper.core.scanengine.ScanOutput
import app.plainpaper.core.scanengine.bridge.platformNativeBridge
import app.plainpaper.feature.capture.processing.PageProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * SC-008 sample on a device: the real repository, processor and engine run many camera-style
 * sessions and imports from a bundled photo, with simulated background/foreground cycles
 * (`PageProcessor.resume()`), and no page may be lost or fail. Counts come from instrumentation
 * arguments `sessions`, `pagesPerSession`, `imports`, `photosPerImport`, `cycles`.
 */
@RunWith(AndroidJUnit4::class)
class CaptureSoakDeviceTest {
    private class DeviceDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    /** Session-folder originals are copied from the bundled asset; JPEG writing uses the real Android implementation. */
    private class SoakFiles(
        private val real: ImageFiles,
        private val source: File,
    ) : ImageFiles by real {
        override suspend fun copyOriginal(
            uri: String,
            destinationPath: String,
        ): CopyResult {
            if (!source.exists()) return CopyResult.Failed(CopyFailure.NotFound)
            source.copyTo(File(destinationPath), overwrite = true)
            return CopyResult.Copied(destinationPath, source.length())
        }

        override suspend fun writeJpeg(
            output: ScanOutput,
            path: String,
            quality: Int,
        ): Boolean = real.writeJpeg(output, path, quality)
    }

    private object NoBackground : BackgroundTask {
        override suspend fun <T> keepAlive(block: suspend () -> T): T = block()
    }

    @Test
    fun sessionsAndImportsNeverLoseOrFailPages() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val args = InstrumentationRegistry.getArguments()
            val sessions = args.getString("sessions")?.toInt() ?: DEFAULT_SESSIONS
            val pagesPerSession = args.getString("pagesPerSession")?.toInt() ?: DEFAULT_PAGES
            val imports = args.getString("imports")?.toInt() ?: DEFAULT_IMPORTS
            val photosPerImport = args.getString("photosPerImport")?.toInt() ?: DEFAULT_PHOTOS
            val cycles = args.getString("cycles")?.toInt() ?: DEFAULT_CYCLES

            val context = instrumentation.targetContext
            val root = File(context.cacheDir, "soak").apply { deleteRecursively(); mkdirs() }
            val source = File(root, "source.jpg")
            instrumentation.context.assets.open("soak-page.jpg").use { input -> source.outputStream().use { input.copyTo(it) } }

            val dispatchers = DeviceDispatchers()
            val repository =
                FileCaptureSessionRepository(FileSystem.SYSTEM, File(root, "capture").path, now = { System.currentTimeMillis() })
            val engine = DefaultScanEngine(platformNativeBridge(), AndroidCapabilityProvider(context), dispatchers)
            val files = SoakFiles(androidImageFiles(context, dispatchers), source)
            val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
            val processor =
                PageProcessor(engine, AndroidPhotoDecoder(dispatchers), files, repository, NoBackground, dispatchers, scope)

            var failures = 0
            var pagesTotal = 0
            val importMillis = mutableListOf<Long>()
            repeat(sessions + imports) { run ->
                val isImport = run >= sessions
                val count = if (isImport) photosPerImport else pagesPerSession
                val source = if (isImport) SessionSource.Import else SessionSource.Camera
                val millis =
                    measureTimeMillis {
                        val session = repository.start(source)
                        val directory = repository.sessionDirectory(session.id)
                        repeat(count) { index ->
                            val original = "$directory/original-${index.toString().padStart(3, '0')}.jpg"
                            files.copyOriginal("asset", original)
                            val page = repository.addPage(session.id, original, PageTag.Document, PageSize.A4)
                            processor.enqueue(session.id, page.id)
                            if (cycles > 0 && index % maxOf(1, count / cycles) == 0) processor.resume() // foreground cycle
                        }
                        val finished =
                            withTimeout(SESSION_TIMEOUT_MILLIS) {
                                repository.activeSession
                                    .filterNotNull()
                                    .first { s -> s.id == session.id && s.unfinishedPages.isEmpty() }
                            }
                        pagesTotal += finished.pageCount
                        failures += finished.pages.count { it.state is PageState.Failed }
                        assertEquals("session $run must keep every page", count, finished.pageCount)
                        finished.pages.forEach { page ->
                            val bytes = page.pagePath?.let { File(it).length() } ?: 0L
                            assertTrue("page ${page.id} must have an image", bytes > 0)
                        }
                        repository.markHandedOff(session.id)
                        repository.discard(session.id)
                    }
                if (isImport) importMillis += millis
            }
            scope.cancel()
            println(
                "soak: $sessions sessions x $pagesPerSession + $imports imports x $photosPerImport = $pagesTotal pages, " +
                    "failures=$failures, import ms=$importMillis",
            )
            assertEquals(0, failures)
            assertEquals(sessions * pagesPerSession + imports * photosPerImport, pagesTotal)
        }

    private companion object {
        const val DEFAULT_SESSIONS = 3
        const val DEFAULT_PAGES = 5
        const val DEFAULT_IMPORTS = 2
        const val DEFAULT_PHOTOS = 10
        const val DEFAULT_CYCLES = 2
        const val SESSION_TIMEOUT_MILLIS = 300_000L
    }
}
