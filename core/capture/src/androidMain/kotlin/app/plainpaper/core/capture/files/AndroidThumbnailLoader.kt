package app.plainpaper.core.capture.files

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.withContext

internal class AndroidThumbnailLoader(
    private val dispatchers: DispatcherProvider,
) : ThumbnailLoader {
    override suspend fun load(
        path: String,
        maxEdge: Int,
    ): ImageBitmap? = withContext(dispatchers.io) { decodeScaled(path, maxEdge)?.asImageBitmap() }
}
