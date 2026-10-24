package app.plainpaper.core.data.capture

import app.plainpaper.core.domain.model.Confidence
import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad
import app.plainpaper.core.domain.model.capture.AttentionReason
import app.plainpaper.core.domain.model.capture.CaptureSession
import app.plainpaper.core.domain.model.capture.CapturedPage
import app.plainpaper.core.domain.model.capture.PageFailure
import app.plainpaper.core.domain.model.capture.PageState
import app.plainpaper.core.domain.model.capture.PageTag
import app.plainpaper.core.domain.model.capture.SessionSource
import app.plainpaper.core.domain.model.capture.SessionStatus
import kotlinx.serialization.Serializable
import okio.Path

internal const val SESSION_SCHEMA_VERSION = 1

/** `session.json` (contracts/storage.md). Paths are relative to the session folder. */
@Serializable
internal data class SessionIndex(
    val schemaVersion: Int = SESSION_SCHEMA_VERSION,
    val id: String,
    val source: String,
    val createdAtEpochMillis: Long,
    val status: String,
    val pages: List<PageIndex> = emptyList(),
)

@Serializable
internal data class PageIndex(
    val id: String,
    val position: Int,
    val originalPath: String,
    val pagePath: String? = null,
    val thumbnailPath: String? = null,
    val state: StateIndex = StateIndex("Pending"),
    val tag: TagIndex = TagIndex("Document"),
    val corners: List<Float>? = null,
    val confidence: String? = null,
    val pageSize: String = "A4",
    val rotationApplied: Int = 0,
)

@Serializable
internal data class StateIndex(
    val type: String,
    val reason: String? = null,
)

@Serializable
internal data class TagIndex(
    val type: String,
    val cardId: String? = null,
)

internal fun CaptureSession.toIndex(directory: Path): SessionIndex =
    SessionIndex(
        id = id,
        source = source.name,
        createdAtEpochMillis = createdAtEpochMillis,
        status = status.name,
        pages = pages.map { it.toIndex(directory) },
    )

/** A page read from disk; a page that was `Processing` when the process died is `Pending` again. */
internal fun SessionIndex.toSession(directory: Path): CaptureSession =
    CaptureSession(
        id = id,
        source = enumOrDefault(source, SessionSource.Camera),
        createdAtEpochMillis = createdAtEpochMillis,
        pages =
            pages.sortedBy { it.position }.mapIndexed {
                index,
                page,
                ->
                page.toPage(directory).copy(position = index)
            },
        status = enumOrDefault(status, SessionStatus.Active),
    )

private fun CapturedPage.toIndex(directory: Path): PageIndex =
    PageIndex(
        id = id,
        position = position,
        originalPath = relativize(originalPath, directory),
        pagePath = pagePath?.let { relativize(it, directory) },
        thumbnailPath = thumbnailPath?.let { relativize(it, directory) },
        state = state.toIndex(),
        tag = tag.toIndex(),
        corners = corners?.toFloatArray()?.toList(),
        confidence = confidence?.name,
        pageSize = pageSize.name,
        rotationApplied = rotationApplied,
    )

private fun PageIndex.toPage(directory: Path): CapturedPage =
    CapturedPage(
        id = id,
        position = position,
        originalPath = resolve(originalPath, directory),
        tag = tag.toTag(),
        pageSize = enumOrDefault(pageSize, PageSize.A4),
        state = state.toState(),
        pagePath = pagePath?.let { resolve(it, directory) },
        thumbnailPath = thumbnailPath?.let { resolve(it, directory) },
        corners =
            corners
                ?.takeIf {
                    it.size == QUAD_VALUES
                }?.let { Quad.fromDoubles(DoubleArray(QUAD_VALUES) { i -> it[i].toDouble() }) },
        confidence = confidence?.let { name -> Confidence.entries.firstOrNull { it.name == name } },
        rotationApplied = rotationApplied,
    )

private const val QUAD_VALUES = 8

private fun PageState.toIndex(): StateIndex =
    when (this) {
        PageState.Pending -> StateIndex("Pending")
        PageState.Processing -> StateIndex("Processing")
        PageState.Ready -> StateIndex("Ready")
        is PageState.NeedsAttention -> StateIndex("NeedsAttention", reason.name)
        is PageState.Failed -> StateIndex("Failed", reason.name)
    }

private fun StateIndex.toState(): PageState =
    when (type) {
        "Ready" -> PageState.Ready
        "NeedsAttention" -> PageState.NeedsAttention(enumOrDefault(reason, AttentionReason.LowConfidence))
        "Failed" -> PageState.Failed(enumOrDefault(reason, PageFailure.Internal))
        else -> PageState.Pending // Pending, Processing (restarted) and anything unknown
    }

private fun PageTag.toIndex(): TagIndex =
    when (this) {
        PageTag.Document -> TagIndex("Document")
        is PageTag.IdFront -> TagIndex("IdFront", cardId)
        is PageTag.IdBack -> TagIndex("IdBack", cardId)
        PageTag.Receipt -> TagIndex("Receipt")
    }

private fun TagIndex.toTag(): PageTag =
    when (type) {
        "IdFront" -> PageTag.IdFront(cardId.orEmpty())
        "IdBack" -> PageTag.IdBack(cardId.orEmpty())
        "Receipt" -> PageTag.Receipt
        else -> PageTag.Document
    }

private inline fun <reified E : Enum<E>> enumOrDefault(
    name: String?,
    default: E,
): E = enumValues<E>().firstOrNull { it.name == name } ?: default

private fun relativize(
    path: String,
    directory: Path,
): String {
    val prefix = "$directory/"
    return if (path.startsWith(prefix)) path.removePrefix(prefix) else path
}

private fun resolve(
    path: String,
    directory: Path,
): String = if (path.startsWith("/")) path else "$directory/$path"

@Suppress("unused")
private fun Point.asList(): List<Float> = listOf(x, y)
