package app.plainpaper.core.capture.background

import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

/** Wraps [block] in a UIKit background task; expiry cancels the block (research R8). */
internal class IosBackgroundTask : BackgroundTask {
    override suspend fun <T> keepAlive(block: suspend () -> T): T =
        coroutineScope {
            val application = UIApplication.sharedApplication
            var task: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
            task =
                application.beginBackgroundTaskWithExpirationHandler {
                    cancel("background time expired")
                    if (task != UIBackgroundTaskInvalid) application.endBackgroundTask(task)
                    task = UIBackgroundTaskInvalid
                }
            try {
                block()
            } finally {
                if (task != UIBackgroundTaskInvalid) application.endBackgroundTask(task)
                task = UIBackgroundTaskInvalid
            }
        }
}
