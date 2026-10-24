package app.plainpaper.feature.capture.fakes

import app.plainpaper.core.capture.background.BackgroundTask

class FakeBackgroundTask : BackgroundTask {
    var keepAliveCount = 0
        private set

    override suspend fun <T> keepAlive(block: suspend () -> T): T {
        keepAliveCount++
        return block()
    }
}
