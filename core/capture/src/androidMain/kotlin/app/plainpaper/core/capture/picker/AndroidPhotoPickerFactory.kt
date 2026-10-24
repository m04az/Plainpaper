package app.plainpaper.core.capture.picker

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CompletableDeferred

private const val FALLBACK_MAX_ITEMS = 100

/** System photo picker (no permission), multi-select (research R6). */
internal class AndroidPhotoPickerFactory : PhotoPickerFactory {
    private class Pending {
        var deferred: CompletableDeferred<List<Uri>>? = null
    }

    @Composable
    override fun remember(): PhotoPicker {
        val pending = remember { Pending() }
        val maxItems = remember { platformMaxItems() }
        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems)) { uris ->
                pending.deferred?.complete(uris)
                pending.deferred = null
            }
        return remember {
            object : PhotoPicker {
                override suspend fun pick(maxItems: Int): List<PickedPhoto> {
                    val deferred = CompletableDeferred<List<Uri>>()
                    pending.deferred = deferred
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    return deferred.await().map { PickedPhoto(it.toString(), it.lastPathSegment) }
                }
            }
        }
    }

    private fun platformMaxItems(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            MediaStore.getPickImagesMaxLimit().coerceAtMost(FALLBACK_MAX_ITEMS).coerceAtLeast(2)
        } else {
            FALLBACK_MAX_ITEMS
        }
}
