package app.plainpaper.feature.capture.fakes

import app.plainpaper.core.capture.picker.PhotoPicker
import app.plainpaper.core.capture.picker.PickedPhoto

class FakePhotoPicker(
    var result: List<PickedPhoto> = emptyList(),
) : PhotoPicker {
    val requestedLimits = mutableListOf<Int>()

    override suspend fun pick(maxItems: Int): List<PickedPhoto> {
        requestedLimits += maxItems
        return result
    }
}
