package app.plainpaper.feature.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.plainpaper.feature.capture.processing.PageProcessor
import org.koin.compose.koinInject

/**
 * Re-queues pending pages of the active session whenever the app comes to the foreground
 * (launch after process death, return from the background): spec 003 FR-011/FR-014.
 */
@Composable
fun CaptureResume() {
    val processor: PageProcessor = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, processor) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) processor.resume() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
