package app.plainpaper.core.domain.repository

import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.CaptureSession
import app.plainpaper.core.domain.model.capture.CapturedPage
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import kotlinx.coroutines.flow.Flow

/** The single active capture session and its files (contracts/kotlin-api.md, storage.md). */
public interface CaptureSessionRepository {
    /** Emits the current session immediately on collection and on every change; null when none. */
    public val activeSession: Flow<CaptureSession?>

    /** Creates a new active session. An existing active session with zero pages is discarded. */
    public suspend fun start(source: SessionSource): CaptureSession

    /** Appends a page whose original is already stored at [originalPath]. */
    public suspend fun addPage(
        sessionId: String,
        originalPath: String,
        tag: PageTag,
        pageSize: PageSize,
    ): CapturedPage

    public suspend fun updatePage(
        sessionId: String,
        page: CapturedPage,
    )

    /** Removes the page from the index and deletes its original and rendered files. */
    public suspend fun removePage(
        sessionId: String,
        pageId: String,
    )

    public suspend fun markHandedOff(sessionId: String)

    /** Deletes the session and all its files. */
    public suspend fun discard(sessionId: String)

    /** A session with pages left over from a previous run (`Active` or `HandedOff`), if any. */
    public suspend fun recoverable(): CaptureSession?

    /** Absolute directory where originals of [sessionId] are written. */
    public fun sessionDirectory(sessionId: String): String
}
