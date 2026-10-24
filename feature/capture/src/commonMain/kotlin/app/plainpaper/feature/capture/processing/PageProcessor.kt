package app.plainpaper.feature.capture.processing

import app.plainpaper.core.capture.background.BackgroundTask
import app.plainpaper.core.capture.files.ImageFiles
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.Confidence
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad
import app.plainpaper.core.domain.model.capture.AttentionReason
import app.plainpaper.core.domain.model.capture.CapturedPage
import app.plainpaper.core.domain.model.capture.PageFailure
import app.plainpaper.core.domain.model.capture.PageSource
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.repository.CaptureSessionRepository
import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.PhotoDecoder
import app.plainpaper.core.scanengine.RenderResult
import app.plainpaper.core.scanengine.ScanAnalysis
import app.plainpaper.core.scanengine.ScanEngine
import app.plainpaper.core.scanengine.ScanPhoto
import app.plainpaper.core.scanengine.model.AnalyzeOptions
import app.plainpaper.core.scanengine.model.Outcome
import app.plainpaper.core.scanengine.model.RenderOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Processing jobs (pages) never surface exceptions to the UI; their outcome is the page state. */
data class PageWork(
    val sessionId: String,
    val pageId: String,
)

/**
 * App-scoped queue that turns originals into Scan-style pages: decode → analyze → render →
 * JPEG + thumbnail → session update (research R8). One page at a time on Simplified devices,
 * two on Full. Survives screen changes; `resume()` re-queues pending pages after a restart.
 */
@Suppress("TooManyFunctions") // queue operations: enqueue, cancel, resume, restyle, transform
class PageProcessor(
    private val engine: ScanEngine,
    private val decoder: PhotoDecoder,
    private val files: ImageFiles,
    private val sessions: CaptureSessionRepository,
    private val background: BackgroundTask,
    dispatchers: DispatcherProvider,
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + dispatchers.default)
    private val queue = Channel<PageWork>(Channel.UNLIMITED)
    private val running = MutableStateFlow<Map<String, Job>>(emptyMap())
    private val cancelledPages = MutableStateFlow<Set<String>>(emptySet())
    private val cancelledSessions = MutableStateFlow<Set<String>>(emptySet())
    private var workers: List<Job>? = null

    /** Ids of pages currently being processed. */
    val inFlight: StateFlow<Set<String>>
        get() = inFlightIds
    private val inFlightIds = MutableStateFlow<Set<String>>(emptySet())

    val concurrency: Int
        get() = if (engine.capability == DeviceCapability.Full) FULL_WORKERS else SIMPLIFIED_WORKERS

    fun enqueue(
        sessionId: String,
        pageId: String,
    ) {
        ensureWorkers()
        cancelledPages.update { it - pageId }
        cancelledSessions.update { it - sessionId }
        queue.trySend(PageWork(sessionId, pageId))
    }

    /** Cancels one page, or every page of [sessionId] when [pageId] is null. */
    fun cancel(
        sessionId: String,
        pageId: String? = null,
    ) {
        if (pageId != null) {
            cancelledPages.update { it + pageId }
            running.value[pageId]?.cancel(CancellationException("page cancelled"))
        } else {
            cancelledSessions.update { it + sessionId }
            running.value.values.forEach { it.cancel(CancellationException("session cancelled")) }
        }
    }

    /** Re-queues every pending page of the active session (launch, foreground). */
    fun resume() {
        scope.launch {
            val session = sessions.activeSession.first() ?: return@launch
            session.pages
                .filter { it.state is PageState.Pending && it.id !in inFlightIds.value }
                .forEach { enqueue(session.id, it.id) }
        }
    }

    private fun ensureWorkers() {
        if (workers != null) return
        workers =
            List(concurrency) {
                scope.launch {
                    for (work in queue) {
                        val job = scope.launch { guarded(work) }
                        running.update { it + (work.pageId to job) }
                        job.join()
                        running.update { it - work.pageId }
                    }
                }
            }
    }

    private suspend fun guarded(work: PageWork) {
        if (work.sessionId in cancelledSessions.value) return
        if (work.pageId in cancelledPages.value) {
            finish(work, null) { it.copy(state = PageState.Failed(PageFailure.Cancelled)) }
            return
        }
        inFlightIds.update { it + work.pageId }
        try {
            background.keepAlive { process(work) }
        } catch (_: CancellationException) {
            if (work.pageId in cancelledPages.value) {
                finish(work, null) { it.copy(state = PageState.Failed(PageFailure.Cancelled)) }
            }
            // A discarded session needs no bookkeeping; a cancelled scope ends the worker anyway.
        } finally {
            inFlightIds.update { it - work.pageId }
        }
    }

    private suspend fun process(work: PageWork) {
        val page = pendingPage(work) ?: return
        sessions.updatePage(work.sessionId, page.copy(state = PageState.Processing))
        when (val decoded = decoder.decode(page.originalPath)) {
            is DecodeResult.Failed -> finish(work, page) { it.copy(state = PageState.Failed(PageFailure.Unreadable)) }
            is DecodeResult.Ready -> analyzeAndStore(work, page, decoded.photo)
        }
    }

    private suspend fun pendingPage(work: PageWork): CapturedPage? =
        sessions.activeSession
            .first()
            ?.takeIf { it.id == work.sessionId }
            ?.page(work.pageId)
            ?.takeIf { it.state is PageState.Pending }

    private suspend fun analyzeAndStore(
        work: PageWork,
        page: CapturedPage,
        photo: ScanPhoto,
    ) {
        // Quick scan pages are already cropped and upright: the full frame is the crop and the
        // engine must not turn them (spec 004 FR-012). Otherwise a page that was already
        // detected keeps its corners: a new look re-renders the same crop.
        val quickScan = page.source == PageSource.QuickScan
        val corners = page.corners ?: if (quickScan) fullFrame(photo) else null
        val options =
            when {
                quickScan -> AnalyzeOptions(userCorners = corners, keepOrientation = true)
                corners != null -> AnalyzeOptions(userCorners = corners)
                else -> AnalyzeOptions()
            }
        when (val analyzed = engine.analyze(photo, options)) {
            is AnalyzeResult.Failed -> {
                finish(
                    work,
                    page,
                ) { it.copy(state = PageState.Failed(analyzed.outcome.toFailure())) }
            }

            is AnalyzeResult.Ready -> {
                analyzed.analysis.use { analysis -> renderAndStore(work, page, analysis) }
            }
        }
    }

    private suspend fun renderAndStore(
        work: PageWork,
        page: CapturedPage,
        analysis: ScanAnalysis,
    ) {
        val rendered = engine.render(analysis, RenderOptions(page.style, page.pageSize))
        if (rendered is RenderResult.Failed) {
            finish(work, page) { it.copy(state = PageState.Failed(rendered.outcome.toFailure())) }
            return
        }
        val output = (rendered as RenderResult.Ready).output
        val directory = sessions.sessionDirectory(work.sessionId)
        val (pagePath, thumbnailPath) = renderedPaths(directory, page)
        if (!files.writeJpeg(output, pagePath, rotation = page.rotation, mirrored = page.mirrored)) {
            finish(work, page) { it.copy(state = PageState.Failed(PageFailure.Internal)) }
            return
        }
        val thumbnailWritten = files.writeThumbnail(pagePath, thumbnailPath)
        // The first detection's verdict stands: a re-render with stored corners reports them as
        // user corners, which would otherwise hide a page that still needs attention.
        val confidence =
            page.confidence ?: if (page.source == PageSource.QuickScan) Confidence.High else analysis.confidence
        finish(work, page) {
            it.copy(
                state = stateFor(analysis.outcome, confidence),
                pagePath = pagePath,
                thumbnailPath = if (thumbnailWritten) thumbnailPath else null,
                corners = page.corners ?: analysis.corners,
                confidence = confidence,
                rotationApplied = analysis.rotationApplied,
            )
        }
    }

    /**
     * Re-renders [pageIds] in [style]. Pages keep their corners; a look rendered before for the
     * same page is reused from its file instead of running the engine again.
     */
    fun restyle(
        sessionId: String,
        pageIds: List<String>,
        style: OutputStyle,
    ) {
        scope.launch {
            val session = sessions.activeSession.first()?.takeIf { it.id == sessionId } ?: return@launch
            val directory = sessions.sessionDirectory(sessionId)
            val pages =
                pageIds.mapNotNull(session::page).filter { page ->
                    page.style != style && page.state !is PageState.Pending && page.state !is PageState.Processing
                }
            for (page in pages) {
                val id = page.id
                val restyled = page.copy(style = style)
                val (pagePath, thumbnailPath) = renderedPaths(directory, restyled)
                if (page.state.hasPage && files.exists(pagePath)) {
                    val thumb = thumbnailPath.takeIf { files.exists(it) }
                    sessions.updatePage(sessionId, restyled.copy(pagePath = pagePath, thumbnailPath = thumb))
                } else {
                    sessions.updatePage(sessionId, restyled.copy(state = PageState.Pending))
                    enqueue(sessionId, id)
                }
            }
        }
    }

    /**
     * Rotates ([rotateClockwise], 90 degrees) and / or mirrors ([flip]) the page as shown, then
     * re-renders it from the original with the stored corners. Pages that are still being
     * processed are left alone (the screens disable the buttons until they are ready).
     */
    fun transform(
        sessionId: String,
        pageId: String,
        rotateClockwise: Boolean = false,
        flip: Boolean = false,
    ) {
        scope.launch {
            val session = sessions.activeSession.first()?.takeIf { it.id == sessionId } ?: return@launch
            val page = session.page(pageId) ?: return@launch
            if (page.state is PageState.Pending || page.state is PageState.Processing) return@launch
            sessions.updatePage(sessionId, transformed(page, rotateClockwise, flip).copy(state = PageState.Pending))
            enqueue(sessionId, pageId)
        }
    }

    private fun transformed(
        page: CapturedPage,
        rotateClockwise: Boolean,
        flip: Boolean,
    ): CapturedPage {
        var rotation = page.rotation
        var mirrored = page.mirrored
        if (rotateClockwise) rotation = (rotation + QUARTER_TURN) % FULL_TURN
        if (flip) {
            // The mirror is applied before the rotation, so mirroring what is on screen
            // left to right means mirroring first and turning the other way.
            mirrored = !mirrored
            rotation = (FULL_TURN - rotation) % FULL_TURN
        }
        return page.copy(rotation = rotation, mirrored = mirrored, revision = page.revision + 1)
    }

    /**
     * One file pair per page, revision and look, so neither switching looks nor rotating ever
     * shows a stale cached image. The page id (not the position) names the files so a removed
     * page cannot collide with the next one.
     */
    private fun renderedPaths(
        directory: String,
        page: CapturedPage,
    ): Pair<String, String> {
        val suffix = "${page.id}-r${page.revision}-${page.style.name.lowercase()}"
        return "$directory/page-$suffix.jpg" to "$directory/thumb-$suffix.jpg"
    }

    /** Writes the final state; a session discarded meanwhile is ignored. */
    private suspend fun finish(
        work: PageWork,
        known: CapturedPage?,
        update: (CapturedPage) -> CapturedPage,
    ) = withContext(NonCancellable) {
        val page =
            known ?: sessions.activeSession
                .first()
                ?.takeIf { it.id == work.sessionId }
                ?.page(work.pageId) ?: return@withContext
        try {
            sessions.updatePage(work.sessionId, update(page))
        } catch (_: IllegalArgumentException) {
            // session no longer active
        }
    }

    private fun stateFor(
        outcome: Outcome,
        confidence: Confidence,
    ): PageState =
        when {
            outcome == Outcome.TooDark -> PageState.NeedsAttention(AttentionReason.TooDark)
            confidence == Confidence.NotFound -> PageState.NeedsAttention(AttentionReason.NoPageFound)
            confidence == Confidence.Low -> PageState.NeedsAttention(AttentionReason.LowConfidence)
            else -> PageState.Ready
        }

    private fun Outcome.toFailure(): PageFailure =
        when (this) {
            Outcome.OutOfMemory -> PageFailure.OutOfMemory
            is Outcome.InvalidInput -> PageFailure.Unreadable
            Outcome.Cancelled -> PageFailure.Cancelled
            else -> PageFailure.Internal
        }

    private fun fullFrame(photo: ScanPhoto): Quad {
        val w = photo.width.toFloat()
        val h = photo.height.toFloat()
        return Quad(Point(0f, 0f), Point(w, 0f), Point(w, h), Point(0f, h))
    }

    private companion object {
        const val FULL_WORKERS = 2
        const val SIMPLIFIED_WORKERS = 1
        const val QUARTER_TURN = 90
        const val FULL_TURN = 360
    }
}
