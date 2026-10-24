package app.plainpaper.core.domain.repository

import app.plainpaper.core.domain.model.capture.CaptureSettings
import kotlinx.coroutines.flow.Flow

public interface CaptureSettingsRepository {
    /** Emits the current value immediately on collection and on every change. */
    public val settings: Flow<CaptureSettings>

    /** Completes only after the value is durably stored. */
    public suspend fun set(settings: CaptureSettings)
}
