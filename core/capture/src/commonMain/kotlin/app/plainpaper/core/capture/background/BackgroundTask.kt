package app.plainpaper.core.capture.background

/**
 * Keeps a batch running while the app is in the background for as long as the system allows
 * (iOS background task; a pass-through on Android). Expiry cancels [block] (research R8).
 */
public interface BackgroundTask {
    public suspend fun <T> keepAlive(block: suspend () -> T): T
}
