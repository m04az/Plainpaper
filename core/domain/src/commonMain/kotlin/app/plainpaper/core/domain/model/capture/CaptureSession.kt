package app.plainpaper.core.domain.model.capture

/** Where a session's pages came from; fixed at creation (data-model.md). */
public enum class SessionSource { Camera, Import }

/** Lifecycle of a capture session. */
public enum class SessionStatus { Active, HandedOff, Discarded }

/**
 * An ordered set of pages created in one camera or import run and handed to review as a
 * whole. Pages are never reordered in this feature; `position` equals the list index.
 */
public data class CaptureSession(
    val id: String,
    val source: SessionSource,
    val createdAtEpochMillis: Long,
    val pages: List<CapturedPage> = emptyList(),
    val status: SessionStatus = SessionStatus.Active,
) {
    init {
        pages.forEachIndexed { index, page ->
            require(page.position == index) { "page ${page.id} at index $index has position ${page.position}" }
        }
    }

    public val pageCount: Int get() = pages.size

    /** Pages still waiting for or undergoing processing. */
    public val unfinishedPages: List<CapturedPage>
        get() = pages.filter { it.state is PageState.Pending || it.state is PageState.Processing }

    public fun page(id: String): CapturedPage? = pages.firstOrNull { it.id == id }

    /** Appends [page] with the next position. */
    public fun withPage(page: CapturedPage): CaptureSession = copy(pages = pages + page.copy(position = pages.size))

    /** Drops the page with [id] and closes the gap so `position` equals the list index. */
    public fun withoutPage(id: String): CaptureSession =
        copy(pages = pages.filter { it.id != id }.mapIndexed { index, page -> page.copy(position = index) })

    /** Replaces the page with the same id; unknown ids are ignored. */
    public fun withUpdatedPage(page: CapturedPage): CaptureSession =
        copy(pages = pages.map { if (it.id == page.id) page.copy(position = it.position) else it })
}
