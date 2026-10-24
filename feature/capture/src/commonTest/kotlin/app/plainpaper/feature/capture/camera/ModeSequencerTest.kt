package app.plainpaper.feature.capture.camera

import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.core.domain.model.capture.PageTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModeSequencerTest {
    private var cards = 0
    private val sequencer = ModeSequencer { "c${++cards}" }

    @Test
    fun idCardAlternatesFrontAndBackPerCard() {
        assertEquals(PageTag.IdFront("c1"), sequencer.next(CaptureMode.IdCard))
        assertTrue(sequencer.awaitingBack)
        assertEquals(PageTag.IdBack("c1"), sequencer.next(CaptureMode.IdCard))
        assertFalse(sequencer.awaitingBack)
        assertEquals(PageTag.IdFront("c2"), sequencer.next(CaptureMode.IdCard))
    }

    @Test
    fun switchingModeEndsTheCard() {
        sequencer.next(CaptureMode.IdCard)
        sequencer.onModeChanged(CaptureMode.Document)
        assertFalse(sequencer.awaitingBack)
        assertEquals(PageTag.Document, sequencer.next(CaptureMode.Document))
        sequencer.onModeChanged(CaptureMode.IdCard)
        assertEquals(PageTag.IdFront("c2"), sequencer.next(CaptureMode.IdCard))
    }

    @Test
    fun documentAndReceiptTagsResetTheCard() {
        sequencer.next(CaptureMode.IdCard)
        assertEquals(PageTag.Receipt, sequencer.next(CaptureMode.Receipt))
        assertFalse(sequencer.awaitingBack)
        assertEquals(PageTag.Document, sequencer.next(CaptureMode.Document))
    }

    @Test
    fun rewindUndoesTheLastCardSide() {
        val front = sequencer.next(CaptureMode.IdCard)
        assertTrue(sequencer.awaitingBack)
        sequencer.rewind(front)
        assertFalse(sequencer.awaitingBack)
        val again = sequencer.next(CaptureMode.IdCard)
        val back = sequencer.next(CaptureMode.IdCard)
        assertFalse(sequencer.awaitingBack)
        sequencer.rewind(back)
        assertTrue(sequencer.awaitingBack)
        assertEquals(PageTag.IdBack((again as PageTag.IdFront).cardId), sequencer.next(CaptureMode.IdCard))
    }
}
