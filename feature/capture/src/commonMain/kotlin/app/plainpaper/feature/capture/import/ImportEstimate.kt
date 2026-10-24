package app.plainpaper.feature.capture.import

/** Seconds left = moving average of the last three conversion durations × remaining, rounded up, min 1 (data-model.md). */
class ImportEstimate(
    private val window: Int = WINDOW,
) {
    private val durations = ArrayDeque<Long>()
    private var lastCompletionMillis: Long? = null

    fun start(nowMillis: Long) {
        lastCompletionMillis = nowMillis
        durations.clear()
    }

    fun onCompleted(nowMillis: Long) {
        val previous = lastCompletionMillis ?: nowMillis
        durations.addLast(maxOf(0L, nowMillis - previous))
        while (durations.size > window) durations.removeFirst()
        lastCompletionMillis = nowMillis
    }

    fun secondsLeft(remaining: Int): Int {
        if (remaining <= 0) return 0
        val average = if (durations.isEmpty()) DEFAULT_MILLIS_PER_PHOTO else durations.average()
        val millis = average * remaining
        return maxOf(1, ((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt())
    }

    private companion object {
        const val WINDOW = 3
        const val DEFAULT_MILLIS_PER_PHOTO = 2_000.0
        const val MILLIS_PER_SECOND = 1_000
    }
}
