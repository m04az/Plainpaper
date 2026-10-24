package app.plainpaper.core.testing

import app.plainpaper.core.domain.model.ProStatus
import app.plainpaper.core.domain.repository.ProStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeProStatusRepository(
    isPro: Boolean = false,
) : ProStatusRepository {
    val state = MutableStateFlow(ProStatus(isPro))
    override val status: Flow<ProStatus> get() = state
}
