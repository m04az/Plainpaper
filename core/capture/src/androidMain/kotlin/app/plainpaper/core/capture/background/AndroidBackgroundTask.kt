package app.plainpaper.core.capture.background

/** Android keeps the process alive without a foreground service for the short batches we run. */
internal class AndroidBackgroundTask : BackgroundTask {
    override suspend fun <T> keepAlive(block: suspend () -> T): T = block()
}
