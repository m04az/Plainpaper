package app.plainpaper.core.domain.repository

import app.plainpaper.core.domain.model.ProStatus
import kotlinx.coroutines.flow.Flow

public interface ProStatusRepository {
    public val status: Flow<ProStatus>
}
