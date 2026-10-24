package app.plainpaper.core.domain.model.capture

import app.plainpaper.core.domain.model.PageSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureSessionTest {
    private val session = CaptureSession(id = "s1", source = SessionSource.Camera, createdAtEpochMillis = 1L)

    private fun page(
        id: String,
        position: Int = 0,
        state: PageState = PageState.Pending,
    ) = CapturedPage(id, position, "original-$id.jpg", PageTag.Document, PageSize.A4, state = state)

    @Test
    fun withPageAppendsWithTheNextPosition() {
        val s = session.withPage(page("a", position = 7)).withPage(page("b"))
        assertEquals(listOf(0, 1), s.pages.map { it.position })
        assertEquals(listOf("a", "b"), s.pages.map { it.id })
        assertEquals(2, s.pageCount)
    }

    @Test
    fun positionsMustMatchIndices() {
        assertFailsWith<IllegalArgumentException> { session.copy(pages = listOf(page("a", position = 1))) }
    }

    @Test
    fun withUpdatedPageReplacesByIdAndKeepsPosition() {
        val s = session.withPage(page("a")).withPage(page("b"))
        val updated = s.withUpdatedPage(page("b", position = 9, state = PageState.Ready))
        assertEquals(1, updated.page("b")?.position)
        assertTrue(updated.page("b")?.isReady == true)
        assertEquals(s, s.withUpdatedPage(page("zzz")))
    }

    @Test
    fun unfinishedPagesArePendingOrProcessing() {
        val s =
            session
                .withPage(page("a", state = PageState.Pending))
                .withPage(page("b", state = PageState.Processing))
                .withPage(page("c", state = PageState.Ready))
                .withPage(page("d", state = PageState.NeedsAttention(AttentionReason.TooDark)))
                .withPage(page("e", state = PageState.Failed(PageFailure.Unreadable)))
        assertEquals(listOf("a", "b"), s.unfinishedPages.map { it.id })
    }

    @Test
    fun hasPageForReadyAndNeedsAttentionOnly() {
        assertTrue(PageState.Ready.hasPage)
        assertTrue(PageState.NeedsAttention(AttentionReason.NoPageFound).hasPage)
        assertFalse(PageState.Pending.hasPage)
        assertFalse(PageState.Processing.hasPage)
        assertFalse(PageState.Failed(PageFailure.Cancelled).hasPage)
    }

    @Test
    fun idCardTagsCompareByCard() {
        assertEquals(PageTag.IdFront("c1"), PageTag.IdFront("c1"))
        assertFalse(PageTag.IdFront("c1") == PageTag.IdBack("c1"))
        assertFalse(PageTag.IdFront("c1") == PageTag.IdFront("c2"))
    }

    @Test
    fun modeResolvesPageSize() {
        assertEquals(PageSize.Letter, CaptureMode.Document.resolvePageSize(PageSize.Letter))
        assertEquals(PageSize.Natural, CaptureMode.IdCard.resolvePageSize(PageSize.Letter))
        assertEquals(PageSize.Natural, CaptureMode.Receipt.resolvePageSize(PageSize.A4))
    }
}
