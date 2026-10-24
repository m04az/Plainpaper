package app.plainpaper.core.capture.picker

import androidx.compose.runtime.Composable

/** A photo chosen in the system picker; [uri] is a content URI (Android) or a file path (iOS). */
public data class PickedPhoto(
    val uri: String,
    val displayName: String? = null,
)

public interface PhotoPicker {
    /** Opens the system picker; an empty list means the person cancelled. */
    public suspend fun pick(maxItems: Int): List<PickedPhoto>
}

public interface PhotoPickerFactory {
    @Composable
    public fun remember(): PhotoPicker
}
