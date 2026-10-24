package app.plainpaper.feature.capture.camera

import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.core.domain.model.capture.PageTag
import kotlin.random.Random

/**
 * Tags captures per mode (spec FR-016): in ID card mode the first capture is the front and
 * the second the back of one card; a third starts a new card. Switching mode ends the card.
 */
class ModeSequencer(
    private val newCardId: () -> String = { Random.nextLong().toString(RADIX) },
) {
    private var currentCard: String? = null

    /** True when the next ID card capture is the back side. */
    val awaitingBack: Boolean get() = currentCard != null

    fun next(mode: CaptureMode): PageTag =
        when (mode) {
            CaptureMode.Document -> {
                currentCard = null
                PageTag.Document
            }

            CaptureMode.Receipt -> {
                currentCard = null
                PageTag.Receipt
            }

            CaptureMode.IdCard -> {
                val card = currentCard
                if (card == null) {
                    PageTag.IdFront(newCardId().also { currentCard = it })
                } else {
                    currentCard = null
                    PageTag.IdBack(card)
                }
            }
        }

    /** Undoes [next] for [tag] after its page was retaken. */
    fun rewind(tag: PageTag) {
        when (tag) {
            is PageTag.IdFront -> {
                currentCard = null
            }

            is PageTag.IdBack -> {
                currentCard = tag.cardId
            }

            else -> {}
        }
    }

    fun onModeChanged(mode: CaptureMode) {
        if (mode != CaptureMode.IdCard) currentCard = null
    }

    private companion object {
        const val RADIX = 36
    }
}
