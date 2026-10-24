package app.plainpaper.core.data.pro

import app.plainpaper.core.domain.model.ProStatus
import app.plainpaper.core.domain.repository.ProStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Everyone is a free user until the Pro purchase feature replaces this (spec FR-013). */
public class FreeOnlyProStatusRepository : ProStatusRepository {
    override val status: Flow<ProStatus> = flowOf(ProStatus.FREE)
}
