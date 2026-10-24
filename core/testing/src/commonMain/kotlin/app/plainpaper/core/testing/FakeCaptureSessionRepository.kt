package app.plainpaper.core.testing

import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.CaptureSession
import app.plainpaper.core.domain.model.capture.CapturedPage
import app.plainpaper.core.domain.model.capture.PageSource
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.model.capture.SessionStatus
import app.plainpaper.core.domain.repository.CaptureSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [CaptureSessionRepository] with deterministic ids. */
class FakeCaptureSessionRepository(
    var now: Long = 1_792_800_000_000L,
    var root: String = "/fake/capture",
) : CaptureSessionRepository {
    val state = MutableStateFlow<CaptureSession?>(null)
    var recoverableSession: CaptureSession? = null
    val discarded = mutableListOf<String>()
    val removedPages = mutableListOf<String>()
    private var sessionCounter = 0
    private var pageCounter = 0

    override val activeSession: Flow<CaptureSession?> get() = state

    override suspend fun start(source: SessionSource): CaptureSession {
        val session = CaptureSession(id = "s${++sessionCounter}", source = source, createdAtEpochMillis = now)
        state.value = session
        return session
    }

    override suspend fun addPage(
        sessionId: String,
        originalPath: String,
        tag: PageTag,
        pageSize: PageSize,
        source: PageSource,
    ): CapturedPage {
        val session = requireSession(sessionId)
        val page =
            CapturedPage(
                id = "p${++pageCounter}",
                position = session.pageCount,
                originalPath = originalPath,
                tag = tag,
                pageSize = pageSize,
                source = source,
            )
        state.value = session.withPage(page)
        return page
    }

    override suspend fun updatePage(
        sessionId: String,
        page: CapturedPage,
    ) {
        state.value = requireSession(sessionId).withUpdatedPage(page)
    }

    override suspend fun removePage(
        sessionId: String,
        pageId: String,
    ) {
        removedPages += pageId
        state.value = requireSession(sessionId).withoutPage(pageId)
    }

    override suspend fun markHandedOff(sessionId: String) {
        state.value = requireSession(sessionId).copy(status = SessionStatus.HandedOff)
    }

    override suspend fun discard(sessionId: String) {
        discarded += sessionId
        if (state.value?.id == sessionId) state.value = null
        if (recoverableSession?.id == sessionId) recoverableSession = null
    }

    /** Like the file repository, a recovered session becomes the active one. */
    override suspend fun recoverable(): CaptureSession? {
        val leftover = recoverableSession ?: state.value?.takeIf { it.pageCount > 0 }
        if (leftover != null && state.value == null) state.value = leftover
        return leftover
    }

    override fun sessionDirectory(sessionId: String): String = "$root/$sessionId"

    private fun requireSession(sessionId: String): CaptureSession =
        requireNotNull(state.value?.takeIf { it.id == sessionId }) { "no active session $sessionId" }
}
