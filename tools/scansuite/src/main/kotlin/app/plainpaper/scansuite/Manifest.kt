package app.plainpaper.scansuite

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Categories of reference photos (contracts/reference-manifest.md). */
@Serializable
enum class Category {
    @SerialName("id-card")
    IdCard,

    @SerialName("receipt")
    Receipt,

    @SerialName("curled")
    Curled,

    @SerialName("folded")
    Folded,

    @SerialName("dark-room")
    DarkRoom,

    @SerialName("hard-shadow")
    HardShadow,

    @SerialName("low-contrast-background")
    LowContrastBackground,

    @SerialName("arabic")
    Arabic,

    @SerialName("english")
    English,

    @SerialName("french")
    French,

    @SerialName("mixed-script")
    MixedScript,

    @SerialName("stamp-signature")
    StampSignature,

    @SerialName("no-page")
    NoPage,

    @SerialName("rotated")
    Rotated,

    @SerialName("pencil")
    Pencil,

    @SerialName("inverted")
    Inverted,

    @SerialName("flash-highlight")
    FlashHighlight,

    @SerialName("open-book")
    OpenBook,

    @SerialName("page-fills-frame")
    PageFillsFrame,

    @SerialName("synthetic")
    Synthetic,
}

@Serializable
enum class Language {
    @SerialName("ar")
    Arabic,

    @SerialName("en")
    English,

    @SerialName("fr")
    French,

    @SerialName("mixed")
    Mixed,

    @SerialName("none")
    None,
}

@Serializable
data class ReferencePhoto(
    val id: String,
    val file: String,
    val tags: Set<Category>,
    val language: Language,
    /** Four `[x, y]` pairs in TL, TR, BR, BL order of the upright page; null only for no-page photos. */
    val expectedCorners: List<List<Double>>? = null,
    /** Rotation (0/90/180/270) the engine must apply to make the page upright. */
    val expectedRotation: Int = 0,
    val transcript: String? = null,
    val cleanReference: String? = null,
    val competitors: Map<String, String> = emptyMap(),
    val device: String? = null,
    val notes: String? = null,
)

@Serializable
data class Manifest(
    val version: Int = 1,
    val photos: List<ReferencePhoto> = emptyList(),
) {
    /** Human-readable problems; empty when valid. */
    fun validate(root: File): List<String> {
        val problems = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (photo in photos) {
            val where = "photo '${photo.id}'"
            if (!ID_PATTERN.matches(photo.id)) problems += "$where: id must match ${ID_PATTERN.pattern}"
            if (!seen.add(photo.id)) problems += "$where: duplicate id"
            val file = File(root, photo.file)
            if (!file.isFile) problems += "$where: missing file ${photo.file}"
            if (photo.tags.isEmpty()) problems += "$where: at least one tag"
            if (photo.expectedRotation !in ROTATIONS) problems += "$where: expectedRotation must be 0, 90, 180 or 270"
            validateCorners(photo, file, where, problems)
            if (photo.transcript != null) {
                if (!File(root, photo.transcript).isFile) problems += "$where: missing transcript ${photo.transcript}"
                if (photo.cleanReference == null) {
                    problems += "$where: a transcript requires a cleanReference image"
                } else if (!File(root, photo.cleanReference).isFile) {
                    problems += "$where: missing cleanReference ${photo.cleanReference}"
                }
            }
            for ((app, path) in photo.competitors) {
                if (app !in COMPETITOR_APPS) problems += "$where: unknown competitor '$app'"
                if (!File(root, path).isFile) problems += "$where: missing competitor output $path"
            }
        }
        return problems
    }

    private fun validateCorners(
        photo: ReferencePhoto,
        file: File,
        where: String,
        problems: MutableList<String>,
    ) {
        val corners = photo.expectedCorners
        if (corners == null) {
            if (Category.NoPage !in
                photo.tags
            ) {
                problems += "$where: expectedCorners may be null only for no-page photos"
            }
            return
        }
        if (corners.size != 4 || corners.any { it.size != 2 }) {
            problems += "$where: expectedCorners must be four [x, y] pairs"
            return
        }
        val size = runCatching { ImageSize.of(file) }.getOrNull() ?: return
        corners.forEachIndexed { index, (x, y) ->
            if (!inside(x, y, size)) problems += "$where: corner $index outside the photo"
        }
        if (!isClockwiseConvex(corners)) problems += "$where: expectedCorners must be TL, TR, BR, BL"
    }

    private fun inside(
        x: Double,
        y: Double,
        size: ImageSize,
    ): Boolean {
        val xOk = x >= -size.width * TOLERANCE && x <= size.width * (1 + TOLERANCE)
        val yOk = y >= -size.height * TOLERANCE && y <= size.height * (1 + TOLERANCE)
        return xOk && yOk
    }

    /**
     * TL, TR, BR, BL of the upright page traverse the quad clockwise on screen (y down) whatever
     * the photo's rotation, so the winding must be clockwise and every turn consistent.
     */
    private fun isClockwiseConvex(corners: List<List<Double>>): Boolean {
        for (i in 0 until 4) {
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            val c = corners[(i + 2) % 4]
            val cross = (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0])
            if (cross <= 0) return false
        }
        return true
    }

    companion object {
        val ID_PATTERN = Regex("^[a-z0-9-]+$")
        val COMPETITOR_APPS = setOf("adobe-scan", "microsoft-lens", "camscanner")
        val ROTATIONS = setOf(0, 90, 180, 270)
        private const val TOLERANCE = 0.02

        private val json =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                encodeDefaults = false
            }

        fun parse(text: String): Manifest = json.decodeFromString(serializer(), text)

        fun load(file: File): Manifest = parse(file.readText())

        fun encode(manifest: Manifest): String = json.encodeToString(serializer(), manifest)
    }
}

/** Pixel size read from the image header without decoding the pixels. */
data class ImageSize(
    val width: Int,
    val height: Int,
) {
    companion object {
        fun of(file: File): ImageSize {
            val stream = javax.imageio.ImageIO.createImageInputStream(file) ?: error("unreadable $file")
            stream.use { input ->
                val reader =
                    javax.imageio.ImageIO
                        .getImageReaders(input)
                        .next()
                try {
                    reader.input = input
                    return ImageSize(reader.getWidth(0), reader.getHeight(0))
                } finally {
                    reader.dispose()
                }
            }
        }
    }
}
