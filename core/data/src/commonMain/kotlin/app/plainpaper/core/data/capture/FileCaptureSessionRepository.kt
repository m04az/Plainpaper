package app.plainpaper.core.data.capture

import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.CaptureSession
import app.plainpaper.core.domain.model.capture.CapturedPage
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.model.capture.SessionStatus
import app.plainpaper.core.domain.repository.CaptureSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random

internal const val SESSION_INDEX_FILE = "session.json"
private const val SESSION_INDEX_TEMP = "session.json.tmp"
private const val ID_BYTES = 8

/**
 * One session folder per capture run under [rootPath] with an atomically rewritten
 * `session.json` (research R7, contracts/storage.md). Not shared between processes.
 */
public class FileCaptureSessionRepository(
    private val fileSystem: FileSystem,
    rootPath: String,
    private val now: () -> Long,
    private val newId: () -> String = { randomId() },
) : CaptureSessionRepository {
    private val root: Path = rootPath.toPath()
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val mutex = Mutex()
    private val state = MutableStateFlow<CaptureSession?>(null)

    override val activeSession: Flow<CaptureSession?> get() = state

    override suspend fun start(source: SessionSource): CaptureSession =
        mutex.withLock {
            state.value?.takeIf { it.pageCount == 0 }?.let { deleteSession(it.id) }
            val session = CaptureSession(id = newId(), source = source, createdAtEpochMillis = now())
            fileSystem.createDirectories(directory(session.id))
            write(session)
            state.value = session
            session
        }

    override suspend fun addPage(
        sessionId: String,
        originalPath: String,
        tag: PageTag,
        pageSize: PageSize,
    ): CapturedPage =
        mutex.withLock {
            val session = requireSession(sessionId)
            val page =
                CapturedPage(
                    id = newId(),
                    position = session.pageCount,
                    originalPath = originalPath,
                    tag = tag,
                    pageSize = pageSize,
                )
            val updated = session.withPage(page)
            write(updated)
            state.value = updated
            page
        }

    override suspend fun updatePage(
        sessionId: String,
        page: CapturedPage,
    ) {
        mutex.withLock {
            val updated = requireSession(sessionId).withUpdatedPage(page)
            write(updated)
            state.value = updated
        }
    }

    override suspend fun markHandedOff(sessionId: String) {
        mutex.withLock {
            val updated = requireSession(sessionId).copy(status = SessionStatus.HandedOff)
            write(updated)
            state.value = updated
        }
    }

    override suspend fun discard(sessionId: String) {
        mutex.withLock {
            deleteSession(sessionId)
            if (state.value?.id == sessionId) state.value = null
        }
    }

    /**
     * The most recent stored session that is `Active` or `HandedOff`. It becomes the active
     * session so the hand-off screen can observe it.
     */
    override suspend fun recoverable(): CaptureSession? =
        mutex.withLock {
            state.value?.takeIf { it.status != SessionStatus.Discarded } ?: loadMostRecent()?.also { state.value = it }
        }

    override fun sessionDirectory(sessionId: String): String = directory(sessionId).toString()

    private fun directory(sessionId: String): Path = root / sessionId

    private fun requireSession(sessionId: String): CaptureSession =
        requireNotNull(state.value?.takeIf { it.id == sessionId }) { "session $sessionId is not active" }

    private fun write(session: CaptureSession) {
        val dir = directory(session.id)
        fileSystem.createDirectories(dir)
        val temp = dir / SESSION_INDEX_TEMP
        fileSystem.write(temp) { writeUtf8(json.encodeToString(SessionIndex.serializer(), session.toIndex(dir))) }
        fileSystem.atomicMove(temp, dir / SESSION_INDEX_FILE)
    }

    private fun read(dir: Path): CaptureSession? =
        try {
            val text = fileSystem.read(dir / SESSION_INDEX_FILE) { readUtf8() }
            json.decodeFromString(SessionIndex.serializer(), text).toSession(dir)
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null // malformed JSON or schema mismatch: not recoverable
        }

    private fun loadMostRecent(): CaptureSession? {
        if (!fileSystem.exists(root)) return null
        return fileSystem
            .list(root)
            .mapNotNull { read(it) }
            .filter { it.status != SessionStatus.Discarded }
            .maxByOrNull { it.createdAtEpochMillis }
    }

    private fun deleteSession(sessionId: String) {
        try {
            fileSystem.deleteRecursively(directory(sessionId), mustExist = false)
        } catch (_: IOException) {
            // Best effort: a folder that cannot be deleted is ignored by loadMostRecent only if
            // its index is gone, so remove the index first on a retry.
        }
    }

    private companion object {
        fun randomId(): String =
            Random
                .nextBytes(
                    ID_BYTES,
                ).joinToString("") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
