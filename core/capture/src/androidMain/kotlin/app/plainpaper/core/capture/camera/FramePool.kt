package app.plainpaper.core.capture.camera

import java.nio.ByteBuffer

/**
 * A few direct buffers the analyzer copies frames into, so the camera image can be closed at
 * once while the collector reads the copy. A frame is dropped when every buffer is busy.
 */
internal class FramePool(
    private val size: Int = POOL_SIZE,
) {
    private class Slot(
        var buffer: ByteBuffer?,
        var busy: Boolean = false,
    )

    private val slots = List(size) { Slot(null) }

    /** Returns a free buffer of at least [bytes], or null when all are busy. */
    @Synchronized
    fun acquire(bytes: Int): Pair<ByteBuffer, () -> Unit>? {
        val slot = slots.firstOrNull { !it.busy } ?: return null
        val buffer =
            slot.buffer?.takeIf { it.capacity() >= bytes } ?: ByteBuffer.allocateDirect(bytes).also { slot.buffer = it }
        slot.busy = true
        buffer.clear()
        return buffer to { release(slot) }
    }

    @Synchronized
    private fun release(slot: Slot) {
        slot.busy = false
    }

    private companion object {
        const val POOL_SIZE = 3
    }
}
