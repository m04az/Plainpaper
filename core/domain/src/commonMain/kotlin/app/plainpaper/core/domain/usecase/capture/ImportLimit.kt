package app.plainpaper.core.domain.usecase.capture

import app.plainpaper.core.domain.repository.ProStatusRepository
import kotlinx.coroutines.flow.first

/** Free users import at most [FREE_LIMIT] photos per import; Pro has no limit (spec FR-013). */
public object ImportLimit {
    public const val FREE_LIMIT: Int = 10

    public sealed interface Decision {
        /** Convert every picked photo. */
        public data class Proceed(
            val count: Int,
        ) : Decision

        /** Ask before converting: import the first [limit] or see Pro. */
        public data class ExceedsFreeLimit(
            val limit: Int,
            val picked: Int,
        ) : Decision
    }

    public fun evaluate(
        pickedCount: Int,
        isPro: Boolean,
    ): Decision =
        if (!isPro && pickedCount > FREE_LIMIT) {
            Decision.ExceedsFreeLimit(limit = FREE_LIMIT, picked = pickedCount)
        } else {
            Decision.Proceed(count = pickedCount)
        }
}

public class EvaluateImportLimitUseCase(
    private val proStatus: ProStatusRepository,
) {
    public suspend operator fun invoke(pickedCount: Int): ImportLimit.Decision =
        ImportLimit.evaluate(pickedCount, proStatus.status.first().isPro)
}
