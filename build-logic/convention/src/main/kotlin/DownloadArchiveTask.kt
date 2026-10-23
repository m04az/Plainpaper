import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Downloads an archive once into a user-level cache (`~/.cache/plainpaper/downloads` by
 * default), verifies its SHA-256 and extracts it with the system `unzip` (which preserves
 * symlinks inside Apple frameworks). Used for the prebuilt OpenCV packages (research R1).
 */
abstract class DownloadArchiveTask : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    /** File name inside the download cache. */
    @get:Input
    abstract val archiveName: Property<String>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        cacheDir.convention(
            project.layout.projectDirectory.dir(
                File(System.getProperty("user.home"), ".cache/plainpaper/downloads").absolutePath,
            ),
        )
    }

    @TaskAction
    fun run() {
        val cache = cacheDir.get().asFile.apply { mkdirs() }
        val archive = File(cache, archiveName.get())
        val expected = sha256.get().lowercase()
        if (!archive.isFile || sha256Of(archive) != expected) {
            logger.lifecycle("Downloading ${url.get()} -> $archive")
            val tmp = File(cache, archiveName.get() + ".part")
            URI(url.get()).toURL().openStream().use { input ->
                Files.copy(input, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val actual = sha256Of(tmp)
            if (actual != expected) {
                tmp.delete()
                throw GradleException("SHA-256 mismatch for ${url.get()}: expected $expected, got $actual")
            }
            Files.move(tmp.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        execOperations.exec {
            commandLine("unzip", "-q", "-o", archive.absolutePath, "-d", out.absolutePath)
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
