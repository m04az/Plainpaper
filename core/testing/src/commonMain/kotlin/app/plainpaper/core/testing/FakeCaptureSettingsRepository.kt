package app.plainpaper.core.testing

import app.plainpaper.core.domain.model.capture.CaptureSettings
import app.plainpaper.core.domain.repository.CaptureSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCaptureSettingsRepository(
    initial: CaptureSettings = CaptureSettings(),
) : CaptureSettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<CaptureSettings> get() = state

    override suspend fun set(settings: CaptureSettings) {
        state.value = settings
    }
}
