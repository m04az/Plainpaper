package app.plainpaper.core.capture.scanner

private const val INDEX_DIGITS = 3
private val ORIGINAL = Regex("""original-(\d+)\.jpg""")

/** The number after the highest existing `original-<n>.jpg`, or 0 for an empty folder. */
internal fun nextOriginalIndex(fileNames: List<String>): Int =
    fileNames
        .mapNotNull {
            ORIGINAL
                .matchEntire(it)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        }.maxOrNull()
        ?.plus(1) ?: 0

internal fun originalName(index: Int): String = "original-${index.toString().padStart(INDEX_DIGITS, '0')}.jpg"
