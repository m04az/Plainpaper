package app.plainpaper.feature.capture.processing

import app.plainpaper.core.capture.background.BackgroundTask
import app.plainpaper.core.capture.files.ImageFiles
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.model.Confidence
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.domain.model.capture.AttentionReason
import app.plainpaper.core.domain.model.capture.CapturedPage
import app.plainpaper.core.domain.model.capture.PageFailure
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.repository.CaptureSessionRepository
import app.plainpaper.core.scanengine.AnalyzeResult
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.PhotoDecoder
import app.plainpaper.core.scanengine.RenderResult
import app.plainpaper.core.scanengine.ScanAnalysis
import app.plainpaper.core.scanengine.ScanEngine
import app.plainpaper.core.scanengine.ScanPhoto
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
internal data class PageWork(
    val sessionId: String,
    val pageId: String,
)

/**
 * App-scoped queue that turns originals into Scan-style pages: decode → analyze → render →
 * JPEG + thumbnail → session update (research R8). One page at a time on Simplified devices,
 * two on Full. Survives screen changes; `resume()` re-queues pending pages after a restart.
 */
internal class PageProcessor(
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
        when (val analyzed = engine.analyze(photo)) {
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
        val rendered = engine.render(analysis, RenderOptions(OutputStyle.Scan, page.pageSize))
        if (rendered is RenderResult.Failed) {
            finish(work, page) { it.copy(state = PageState.Failed(rendered.outcome.toFailure())) }
            return
        }
        val output = (rendered as RenderResult.Ready).output
        val directory = sessions.sessionDirectory(work.sessionId)
        val index = page.position.toString().padStart(INDEX_DIGITS, '0')
        val pagePath = "$directory/page-$index.jpg"
        val thumbnailPath = "$directory/thumb-$index.jpg"
        if (!files.writeJpeg(output, pagePath)) {
            finish(work, page) { it.copy(state = PageState.Failed(PageFailure.Internal)) }
            return
        }
        val thumbnailWritten = files.writeThumbnail(pagePath, thumbnailPath)
        finish(work, page) {
            it.copy(
                state = analysis.toPageState(),
                pagePath = pagePath,
                thumbnailPath = if (thumbnailWritten) thumbnailPath else null,
                corners = analysis.corners,
                confidence = analysis.confidence,
                rotationApplied = analysis.rotationApplied,
            )
        }
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

    private fun ScanAnalysis.toPageState(): PageState =
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

    private companion object {
        const val FULL_WORKERS = 2
        const val SIMPLIFIED_WORKERS = 1
        const val INDEX_DIGITS = 3
    }
}
