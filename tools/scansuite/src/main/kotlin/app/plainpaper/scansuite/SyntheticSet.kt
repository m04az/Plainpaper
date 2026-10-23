package app.plainpaper.scansuite

import app.plainpaper.scansuite.synthetic.SyntheticSetGenerator
import java.io.File

/** Generates the synthetic set once per generator version into the build directory. */
object SyntheticSet {
    fun ensure(
        repositoryRoot: File,
        dir: File,
    ): ManifestSource {
        val stamp = File(dir, "generator.version")
        val expected = "${SyntheticSetGenerator.VERSION}:42\n"
        val manifestFile = File(dir, "manifest.json")
        val manifest =
            if (stamp.isFile && stamp.readText() == expected && manifestFile.isFile) {
                Manifest.load(manifestFile)
            } else {
                dir.deleteRecursively()
                SyntheticSetGenerator(repositoryRoot, dir).generate()
            }
        return ManifestSource(dir, manifest, synthetic = true)
    }
}
