package app.plainpaper.core.data.capture

import app.plainpaper.core.domain.model.Confidence
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad
import app.plainpaper.core.domain.model.capture.PageSource
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.model.capture.SessionStatus
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileCaptureSessionRepositoryTest {
    private val fs = FakeFileSystem()
    private val root = "/data/capture"
    private var clock = 1_000L
    private var ids = 0
    private val repository = FileCaptureSessionRepository(fs, root, now = { clock }, newId = { "id${++ids}" })

    private fun indexText(sessionId: String) = fs.read("$root/$sessionId/$SESSION_INDEX_FILE".toPath()) { readUtf8() }

    @Test
    fun startCreatesTheFolderAndIndex() =
        runTest {
            val session = repository.start(SessionSource.Camera)
            assertEquals("id1", session.id)
            assertEquals(1_000L, session.createdAtEpochMillis)
            assertTrue(fs.exists("$root/id1".toPath()))
            assertTrue(indexText("id1").contains("\"status\":\"Active\""))
            assertEquals("$root/id1", repository.sessionDirectory("id1"))
        }

    @Test
    fun pagesKeepOrderAndRelativePaths() =
        runTest {
            val session = repository.start(SessionSource.Import)
            val a = repository.addPage(session.id, "$root/id1/original-000.jpg", PageTag.Document, PageSize.A4)
            val b = repository.addPage(session.id, "/elsewhere/photo.jpg", PageTag.Receipt, PageSize.Natural)
            assertEquals(listOf(0, 1), listOf(a.position, b.position))
            val text = indexText("id1")
            assertTrue(text.contains("\"originalPath\":\"original-000.jpg\""), text)
            assertTrue(text.contains("\"originalPath\":\"/elsewhere/photo.jpg\""), text)
            assertEquals(
                "$root/id1/original-000.jpg",
                repository
                    .recoverable()
                    ?.pages
                    ?.first()
                    ?.originalPath,
            )
        }

    @Test
    fun updatePageIsAtomicAndRoundTrips() =
        runTest {
            val session = repository.start(SessionSource.Camera)
            val page =
                repository.addPage(
                    session.id,
                    "$root/id1/original-000.jpg",
                    PageTag.IdFront("c1"),
                    PageSize.Natural,
                )
            val quad = Quad(Point(1f, 2f), Point(3f, 2f), Point(3f, 5f), Point(1f, 5f))
            repository.updatePage(
                session.id,
                page.copy(
                    state = PageState.Ready,
                    pagePath = "$root/id1/page-000.jpg",
                    thumbnailPath = "$root/id1/thumb-000.jpg",
                    corners = quad,
                    confidence = Confidence.High,
                    rotationApplied = 90,
                    style = OutputStyle.Scan,
                ),
            )
            assertFalse(fs.exists("$root/id1/session.json.tmp".toPath()))
            val loaded = FileCaptureSessionRepository(fs, root, now = { clock }).recoverable()
            val stored = assertNotNull(loaded).pages.single()
            assertEquals(PageState.Ready, stored.state)
            assertEquals(quad, stored.corners)
            assertEquals(Confidence.High, stored.confidence)
            assertEquals(90, stored.rotationApplied)
            assertEquals(PageTag.IdFront("c1"), stored.tag)
            assertEquals(PageSize.Natural, stored.pageSize)
            assertEquals(OutputStyle.Scan, stored.style)
            assertEquals("$root/id1/page-000.jpg", stored.pagePath)
        }

    @Test
    fun processingPagesComeBackPending() =
        runTest {
            val session = repository.start(SessionSource.Camera)
            val page = repository.addPage(session.id, "$root/id1/o.jpg", PageTag.Document, PageSize.A4)
            repository.updatePage(session.id, page.copy(state = PageState.Processing))
            val fresh = FileCaptureSessionRepository(fs, root, now = { clock })
            assertEquals(
                PageState.Pending,
                fresh
                    .recoverable()
                    ?.pages
                    ?.single()
                    ?.state,
            )
        }

    @Test
    fun recoverableIsNullWithoutSessionsAndSetsTheActiveSession() =
        runTest {
            assertNull(repository.recoverable())
            repository.start(SessionSource.Camera)
            repository.addPage("id1", "$root/id1/o.jpg", PageTag.Document, PageSize.A4)
            val fresh = FileCaptureSessionRepository(fs, root, now = { clock })
            val recovered = assertNotNull(fresh.recoverable())
            assertEquals("id1", recovered.id)
            assertEquals("id1", fresh.recoverable()?.id)
        }

    @Test
    fun startDiscardsAnEmptyActiveSessionButKeepsOneWithPages() =
        runTest {
            repository.start(SessionSource.Camera) // id1, empty
            repository.start(SessionSource.Camera) // id2 replaces it
            assertFalse(fs.exists("$root/id1".toPath()))
            repository.addPage("id2", "$root/id2/o.jpg", PageTag.Document, PageSize.A4)
            clock = 2_000L
            repository.start(SessionSource.Import) // id4 (page id3 consumed an id)
            assertTrue(fs.exists("$root/id2".toPath()))
            val fresh = FileCaptureSessionRepository(fs, root, now = { clock })
            // The newest session is empty: only sessions with pages are worth recovering.
            assertEquals("id2", fresh.recoverable()?.id)
        }

    @Test
    fun startDeletesStaleEmptySessionsOfEarlierRuns() =
        runTest {
            repository.start(SessionSource.Camera) // id1, empty, left behind by a previous run
            val fresh = FileCaptureSessionRepository(fs, root, now = { clock }, newId = { "fresh" })
            fresh.start(SessionSource.Camera)
            assertFalse(fs.exists("$root/id1".toPath()))
            assertTrue(fs.exists("$root/fresh".toPath()))
        }

    @Test
    fun discardDeletesTheFolder() =
        runTest {
            val session = repository.start(SessionSource.Camera)
            repository.addPage(session.id, "$root/id1/o.jpg", PageTag.Document, PageSize.A4)
            fs.write("$root/id1/o.jpg".toPath()) { writeUtf8("x") }
            repository.discard(session.id)
            assertFalse(fs.exists("$root/id1".toPath()))
            assertNull(repository.recoverable())
        }

    @Test
    fun handedOffStatusIsStored() =
        runTest {
            val session = repository.start(SessionSource.Camera)
            repository.addPage(session.id, "$root/id1/o.jpg", PageTag.Document, PageSize.A4)
            repository.markHandedOff(session.id)
            assertEquals(
                SessionStatus.HandedOff,
                FileCaptureSessionRepository(fs, root, now = { clock }).recoverable()?.status,
            )
        }

    @Test
    fun unknownKeysAndMalformedFilesAreTolerated() =
        runTest {
            fs.createDirectories("$root/old".toPath())
            fs.write("$root/old/$SESSION_INDEX_FILE".toPath()) {
                writeUtf8(
                    """{"schemaVersion":1,"id":"old","source":"Camera","createdAtEpochMillis":5,"status":"Active",
                    "futureField":true,"pages":[{"id":"p","position":0,"originalPath":"o.jpg","extra":1}]}""",
                )
            }
            fs.createDirectories("$root/broken".toPath())
            fs.write("$root/broken/$SESSION_INDEX_FILE".toPath()) { writeUtf8("not json") }
            val recovered = assertNotNull(repository.recoverable())
            assertEquals("old", recovered.id)
            assertEquals("$root/old/o.jpg", recovered.pages.single().originalPath)
            assertEquals(OutputStyle.ColorDocument, recovered.pages.single().style, "older indexes default to Enhanced")
        }

    @Test
    fun rotationMirrorAndRevisionRoundTripAndOldIndexesDefault() =
        runTest {
            val session = repository.start(SessionSource.Camera)
            val page = repository.addPage(session.id, "$root/id1/original-000.jpg", PageTag.Document, PageSize.A4)
            repository.updatePage(session.id, page.copy(rotation = 270, mirrored = true, revision = 3))
            val stored =
                assertNotNull(
                    FileCaptureSessionRepository(fs, root, now = { clock }).recoverable(),
                ).pages.single()
            assertEquals(Triple(270, true, 3), Triple(stored.rotation, stored.mirrored, stored.revision))

            val old = indexText("id1").replace(",\"rotation\":270,\"mirrored\":true,\"revision\":3", "")
            fs.write("$root/id1/$SESSION_INDEX_FILE".toPath()) { writeUtf8(old) }
            val legacy =
                assertNotNull(
                    FileCaptureSessionRepository(fs, root, now = { clock }).recoverable(),
                ).pages.single()
            assertEquals(Triple(0, false, 0), Triple(legacy.rotation, legacy.mirrored, legacy.revision))
        }

    @Test
    fun removePageDeletesItsFilesAndKeepsTheOthers() =
        runTest {
            val session = repository.start(SessionSource.Camera)
            val a = repository.addPage(session.id, "$root/id1/original-a.jpg", PageTag.Document, PageSize.A4)
            val b = repository.addPage(session.id, "$root/id1/original-b.jpg", PageTag.Document, PageSize.A4)
            for (name in listOf(
                "original-a.jpg",
                "original-b.jpg",
                "page-${a.id}-r0-Scan.jpg",
                "thumb-${a.id}-r0-Scan.jpg",
                "page-${b.id}-r0-Scan.jpg",
            )) {
                fs.write("$root/id1/$name".toPath()) { writeUtf8("x") }
            }
            repository.removePage(session.id, a.id)
            assertFalse(fs.exists("$root/id1/original-a.jpg".toPath()))
            assertFalse(fs.exists("$root/id1/page-${a.id}-r0-Scan.jpg".toPath()))
            assertFalse(fs.exists("$root/id1/thumb-${a.id}-r0-Scan.jpg".toPath()))
            assertTrue(fs.exists("$root/id1/original-b.jpg".toPath()))
            assertTrue(fs.exists("$root/id1/page-${b.id}-r0-Scan.jpg".toPath()))
            val stored = assertNotNull(FileCaptureSessionRepository(fs, root, now = { clock }).recoverable())
            assertEquals(listOf(b.id), stored.pages.map { it.id })
        }

    @Test
    fun pageSourceRoundTripsAndOldIndexesDefaultFromTheSessionSource() =
        runTest {
            val session = repository.start(SessionSource.Camera)
            repository.addPage(session.id, "$root/id1/a.jpg", PageTag.Document, PageSize.A4, PageSource.QuickScan)
            repository.addPage(session.id, "$root/id1/b.jpg", PageTag.Document, PageSize.A4)
            val reloaded = assertNotNull(FileCaptureSessionRepository(fs, root, now = { clock }).recoverable())
            assertEquals(listOf(PageSource.QuickScan, PageSource.Camera), reloaded.pages.map { it.source })

            assertEquals(PageSource.Camera, oldIndexSource("Camera"))
            assertEquals(PageSource.Import, oldIndexSource("Import"))
        }

    private suspend fun oldIndexSource(sessionSource: String): PageSource {
        val oldFs = FakeFileSystem()
        oldFs.createDirectories("$root/old".toPath())
        oldFs.write("$root/old/$SESSION_INDEX_FILE".toPath()) {
            writeUtf8(
                """{"schemaVersion":1,"id":"old","source":"$sessionSource","createdAtEpochMillis":5,"status":"Active",
                "pages":[{"id":"p","position":0,"originalPath":"o.jpg"}]}""",
            )
        }
        return assertNotNull(
            FileCaptureSessionRepository(oldFs, root, now = { clock }).recoverable(),
        ).pages.single().source
    }
}
