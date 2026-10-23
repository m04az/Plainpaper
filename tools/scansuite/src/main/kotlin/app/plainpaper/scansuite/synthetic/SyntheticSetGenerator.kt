package app.plainpaper.scansuite.synthetic

import app.plainpaper.scansuite.Category
import app.plainpaper.scansuite.Language
import app.plainpaper.scansuite.Manifest
import app.plainpaper.scansuite.ReferencePhoto
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Deterministic synthetic reference set (research R13b): rendered pages with known corners,
 * transcripts and clean references, warped onto a desk with shadows, rotations and noise.
 */
class SyntheticSetGenerator(
    private val repositoryRoot: File,
    private val outputDir: File,
    private val seed: Long = 42L,
) {
    /** Scene difficulty (research R13b, User Story 3). */
    enum class Scene(
        val light: Double,
        val tag: Category?,
    ) {
        Normal(1.0, null),
        Dark(0.22, Category.DarkRoom),
        VeryDark(0.07, Category.DarkRoom),
        LowContrast(1.0, Category.LowContrastBackground),
        NoPage(1.0, Category.NoPage),
        Curled(1.0, Category.Curled),
    }

    data class Variant(
        val language: Language,
        val shadow: Boolean,
        val rotation: Int,
        val stamp: Boolean = false,
        val scene: Scene = Scene.Normal,
    ) {
        val id: String
            get() =
                "synthetic-${language.name.lowercase()}-${if (shadow) "shadow" else "plain"}" +
                    (if (stamp) "-stamp" else "") +
                    (if (scene != Scene.Normal) "-${scene.name.lowercase()}" else "") +
                    "-r$rotation"
    }

    fun variants(): List<Variant> =
        buildList {
            for (language in listOf(Language.English, Language.French, Language.Arabic)) {
                for (shadow in listOf(false, true)) {
                    for (rotation in listOf(0, 90, 180, 270)) add(Variant(language, shadow, rotation))
                }
            }
            // Colour content for the Color document style: a red stamp and a blue signature.
            add(Variant(Language.English, shadow = false, rotation = 0, stamp = true))
            add(Variant(Language.Arabic, shadow = true, rotation = 0, stamp = true))
            // Hard scenes (User Story 3).
            add(Variant(Language.English, shadow = false, rotation = 0, scene = Scene.Dark))
            add(Variant(Language.Arabic, shadow = false, rotation = 0, scene = Scene.Dark))
            add(Variant(Language.English, shadow = false, rotation = 0, scene = Scene.VeryDark))
            add(Variant(Language.English, shadow = false, rotation = 0, scene = Scene.LowContrast))
            add(Variant(Language.French, shadow = true, rotation = 0, scene = Scene.LowContrast))
            add(Variant(Language.None, shadow = false, rotation = 0, scene = Scene.NoPage))
            add(Variant(Language.None, shadow = true, rotation = 0, scene = Scene.NoPage))
            add(Variant(Language.English, shadow = false, rotation = 0, scene = Scene.Curled))
            add(Variant(Language.Arabic, shadow = true, rotation = 0, scene = Scene.Curled))
        }

    fun generate(): Manifest {
        outputDir.mkdirs()
        listOf("photos", "clean", "transcripts").forEach { File(outputDir, it).mkdirs() }
        val photos =
            variants().mapIndexed { index, variant ->
                val random = Random(seed + index)
                val hasPage = variant.scene != Scene.NoPage
                val page =
                    renderPage(
                        if (variant.language ==
                            Language.None
                        ) {
                            Language.English
                        } else {
                            variant.language
                        },
                        variant.stamp,
                    )
                val photo = composePhoto(page.image, variant, random)
                val photoFile = "photos/${variant.id}.jpg"
                writeJpeg(photo.image, File(outputDir, photoFile))
                val cleanFile = if (hasPage) "clean/${variant.id}.png" else null
                val transcriptFile = if (hasPage) "transcripts/${variant.id}.txt" else null
                if (hasPage) {
                    ImageIO.write(page.image, "png", File(outputDir, cleanFile!!))
                    File(outputDir, transcriptFile!!).writeText(page.lines.joinToString("\n") + "\n")
                }
                manifestEntry(variant, photoFile, cleanFile, transcriptFile, if (hasPage) photo.corners else null)
            }
        val manifest = Manifest(version = 1, photos = photos)
        File(outputDir, "manifest.json").writeText(Manifest.encode(manifest) + "\n")
        File(outputDir, "generator.version").writeText("$VERSION:$seed\n")
        return manifest
    }

    private fun manifestEntry(
        variant: Variant,
        photoFile: String,
        cleanFile: String?,
        transcriptFile: String?,
        corners: List<Pair<Double, Double>>?,
    ): ReferencePhoto =
        ReferencePhoto(
            id = variant.id,
            file = photoFile,
            tags =
                buildSet {
                    add(Category.Synthetic)
                    when (variant.language) {
                        Language.Arabic -> add(Category.Arabic)
                        Language.French -> add(Category.French)
                        Language.None -> Unit
                        else -> add(Category.English)
                    }
                    if (variant.shadow) add(Category.HardShadow)
                    if (variant.rotation != 0) add(Category.Rotated)
                    if (variant.stamp) add(Category.StampSignature)
                    variant.scene.tag?.let { add(it) }
                },
            language = variant.language,
            expectedCorners = corners?.map { listOf(round1(it.first), round1(it.second)) },
            expectedRotation = (360 - variant.rotation) % 360,
            transcript = transcriptFile,
            cleanReference = cleanFile,
            notes = "generated by SyntheticSetGenerator v$VERSION seed $seed",
        )

    // ---- page rendering --------------------------------------------------------------------

    class RenderedPage(
        val image: BufferedImage,
        val lines: List<String>,
    )

    private fun font(language: Language): Font {
        val file =
            if (language == Language.Arabic) {
                "core/designsystem/src/commonMain/composeResources/font/ibm_plex_sans_arabic_regular.ttf"
            } else {
                "core/designsystem/src/commonMain/composeResources/font/inter_regular.ttf"
            }
        val base = Font.createFont(Font.TRUETYPE_FONT, File(repositoryRoot, file))
        return base.deriveFont(if (language == Language.Arabic) ARABIC_FONT_SIZE else LATIN_FONT_SIZE)
    }

    private fun text(language: Language): String {
        val name =
            when (language) {
                Language.Arabic -> "ar"
                Language.French -> "fr"
                else -> "en"
            }
        return requireNotNull(
            SyntheticSetGenerator::class.java.getResource("/synthetic/$name.txt"),
        ) { "missing $name.txt" }
            .readText()
    }

    fun renderPage(
        language: Language,
        stamp: Boolean = false,
    ): RenderedPage {
        val image = BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT)
        g.color = Color.BLACK
        val font = font(language)
        g.font = font
        val frc = g.fontRenderContext
        val rtl = language == Language.Arabic
        val maxWidth = PAGE_WIDTH - 2 * MARGIN
        val lines = mutableListOf<String>()
        var y = MARGIN + LINE_HEIGHT
        for (paragraph in text(language).lines().filter { it.isNotBlank() }) {
            var current = StringBuilder()
            for (word in paragraph.trim().split(Regex("\\s+"))) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (TextLayout(candidate, font, frc).advance <= maxWidth || current.isEmpty()) {
                    current = StringBuilder(candidate)
                } else {
                    lines += current.toString()
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
            lines += "" // paragraph gap marker (not drawn, not transcribed)
        }
        val drawn = mutableListOf<String>()
        for (line in lines) {
            if (y > PAGE_HEIGHT - MARGIN) break
            if (line.isEmpty()) {
                y += LINE_HEIGHT / 2
            } else {
                val layout = TextLayout(line, font, frc)
                val x = if (rtl) PAGE_WIDTH - MARGIN - layout.advance else MARGIN.toFloat()
                layout.draw(g, x, y.toFloat())
                drawn += line
                y += LINE_HEIGHT
            }
        }
        if (stamp) StampArt.draw(g)
        g.dispose()
        return RenderedPage(image, drawn)
    }

    // ---- photo composition -----------------------------------------------------------------

    class ComposedPhoto(
        val image: BufferedImage,
        /** TL, TR, BR, BL of the upright page in photo coordinates. */
        val corners: List<Pair<Double, Double>>,
    )

    fun composePhoto(
        page: BufferedImage,
        variant: Variant,
        random: Random,
    ): ComposedPhoto {
        val w = CANVAS_WIDTH
        val h = CANVAS_HEIGHT
        val insetX = w * 0.12
        val insetY = h * 0.07

        fun jitter() = (random.nextDouble() * 2 - 1) * w * 0.05
        val dst =
            listOf(
                Pair(insetX + jitter(), insetY + jitter()),
                Pair(w - insetX + jitter(), insetY + jitter()),
                Pair(w - insetX + jitter(), h - insetY + jitter()),
                Pair(insetX + jitter(), h - insetY + jitter()),
            )
        val src =
            listOf(
                Pair(0.0, 0.0),
                Pair(page.width.toDouble(), 0.0),
                Pair(page.width.toDouble(), page.height.toDouble()),
                Pair(0.0, page.height.toDouble()),
            )
        val inverse = Homography.from(src, dst).inverse()
        val scene = variant.scene
        val desk = if (scene == Scene.LowContrast) LOW_CONTRAST_DESK else DESK
        val curlAmplitude = if (scene == Scene.Curled) CURL_AMPLITUDE_PX else 0.0
        val pagePixels = IntArray(page.width * page.height)
        page.getRGB(0, 0, page.width, page.height, pagePixels, 0, page.width)
        val canvas = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val row = IntArray(w)
        val noise = Random(random.nextLong())
        val shadowAngle = random.nextDouble() * Math.PI
        val shadowCos = kotlin.math.cos(shadowAngle)
        val shadowSin = kotlin.math.sin(shadowAngle)
        val shadowOffset = random.nextDouble(0.3, 0.7) * (w * shadowCos + h * shadowSin)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val (u, v0) = inverse.apply(x + 0.5, y + 0.5)
                // Single-axis curl: content bows vertically as a function of u (research R7).
                val v = v0 + curlAmplitude * kotlin.math.sin(Math.PI * u / page.width)
                var r: Double
                var g: Double
                var b: Double
                if (scene != Scene.NoPage && insidePage(u, v, page.width, page.height)) {
                    val rgbValue = bilinearRgb(pagePixels, page.width, page.height, u, v)
                    r = rgbValue[0] * PAPER_R
                    g = rgbValue[1] * PAPER_G
                    b = rgbValue[2] * PAPER_B
                } else {
                    val texture = 1.0 + (noise.nextDouble() - 0.5) * 0.12
                    r = desk[0] * texture
                    g = desk[1] * texture
                    b = desk[2] * texture
                }
                // Illumination: gentle horizontal gradient always; on request a cast shadow with a
                // soft penumbra (real shadows are never a hard step).
                var light = (0.88 + 0.12 * x / w) * scene.light
                if (variant.shadow) {
                    val distance = x * shadowCos + y * shadowSin - shadowOffset
                    val t = ((distance + PENUMBRA_PX) / (2 * PENUMBRA_PX)).coerceIn(0.0, 1.0)
                    light *= SHADOW_DEPTH + (1 - SHADOW_DEPTH) * t * t * (3 - 2 * t)
                }
                val n = (noise.nextDouble() - 0.5) * NOISE_AMPLITUDE
                row[x] = rgb(r * light + n, g * light + n, b * light + n)
            }
            canvas.setRGB(0, y, w, 1, row, 0, w)
        }
        val rotated = Rotation.rotate(canvas, variant.rotation)
        val corners = dst.map { Rotation.rotatePoint(it, variant.rotation, w, h) }
        return ComposedPhoto(rotated, corners)
    }

    private fun insidePage(
        u: Double,
        v: Double,
        width: Int,
        height: Int,
    ): Boolean = u >= 0 && v >= 0 && u < width && v < height

    /** Bilinear sample of an RGB page as three 0..1 channel values (relative to the page's white). */
    private fun bilinearRgb(
        pixels: IntArray,
        width: Int,
        height: Int,
        u: Double,
        v: Double,
    ): DoubleArray {
        val x0 = u.toInt().coerceIn(0, width - 1)
        val y0 = v.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = (u - x0).coerceIn(0.0, 1.0)
        val fy = (v - y0).coerceIn(0.0, 1.0)
        val out = DoubleArray(3)
        for (c in 0 until 3) {
            val shift = 16 - 8 * c

            fun at(
                x: Int,
                y: Int,
            ) = ((pixels[y * width + x] shr shift) and 0xFF) / 255.0
            val top = at(x0, y0) * (1 - fx) + at(x1, y0) * fx
            val bottom = at(x0, y1) * (1 - fx) + at(x1, y1) * fx
            out[c] = top * (1 - fy) + bottom * fy
        }
        return out
    }

    private fun rgb(
        r: Double,
        g: Double,
        b: Double,
    ): Int {
        val ri = r.roundToInt().coerceIn(0, 255)
        val gi = g.roundToInt().coerceIn(0, 255)
        val bi = b.roundToInt().coerceIn(0, 255)
        return (ri shl 16) or (gi shl 8) or bi
    }

    private fun writeJpeg(
        image: BufferedImage,
        file: File,
    ) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params =
            writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = JPEG_QUALITY
            }
        ImageIO.createImageOutputStream(file).use { output ->
            writer.output = output
            writer.write(null, IIOImage(image, null, null), params)
        }
        writer.dispose()
    }

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

    companion object {
        const val VERSION = 4
        const val PAGE_WIDTH = 1240 // A4 at 150 DPI
        const val PAGE_HEIGHT = 1754
        const val CANVAS_WIDTH = 1500
        const val CANVAS_HEIGHT = 2000
        private const val MARGIN = 110
        private const val LINE_HEIGHT = 48
        private const val LATIN_FONT_SIZE = 30f
        private const val ARABIC_FONT_SIZE = 32f
        private const val PAPER_R = 252.0
        private const val PAPER_G = 250.0
        private const val PAPER_B = 246.0
        private val DESK = doubleArrayOf(112.0, 92.0, 74.0)
        private val LOW_CONTRAST_DESK = doubleArrayOf(236.0, 231.0, 222.0)
        private const val CURL_AMPLITUDE_PX = 40.0
        private const val NOISE_AMPLITUDE = 10.0
        private const val PENUMBRA_PX = 24.0
        private const val SHADOW_DEPTH = 0.55
        private const val JPEG_QUALITY = 0.8f
        const val STAMP_CENTER_X = StampArt.CENTER_X
        const val STAMP_CENTER_Y = StampArt.CENTER_Y
        const val STAMP_RADIUS = StampArt.RADIUS
    }
}
