package app.plainpaper.core.domain.usecase.capture

import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.CaptureSession
import app.plainpaper.core.domain.model.capture.CapturedPage
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.repository.CaptureSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

public class ObserveActiveSessionUseCase(
    private val repository: CaptureSessionRepository,
) {
    public operator fun invoke(): Flow<CaptureSession?> = repository.activeSession.distinctUntilChanged()
}

public class StartSessionUseCase(
    private val repository: CaptureSessionRepository,
) {
    public suspend operator fun invoke(source: SessionSource): CaptureSession = repository.start(source)
}

public class AddCapturedPageUseCase(
    private val repository: CaptureSessionRepository,
) {
    public suspend operator fun invoke(
        sessionId: String,
        originalPath: String,
        tag: PageTag,
        pageSize: PageSize,
    ): CapturedPage = repository.addPage(sessionId, originalPath, tag, pageSize)
}

public class FinishSessionUseCase(
    private val repository: CaptureSessionRepository,
) {
    public suspend operator fun invoke(sessionId: String): Unit = repository.markHandedOff(sessionId)
}

public class DiscardSessionUseCase(
    private val repository: CaptureSessionRepository,
) {
    public suspend operator fun invoke(sessionId: String): Unit = repository.discard(sessionId)
}

public class RecoverSessionUseCase(
    private val repository: CaptureSessionRepository,
) {
    /** The session left over from a previous run when it still has pages, otherwise null. */
    public suspend operator fun invoke(): CaptureSession? = repository.recoverable()?.takeIf { it.pageCount > 0 }
}
