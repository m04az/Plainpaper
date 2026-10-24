package app.plainpaper.scansuite

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import java.io.File

/**
 * `./gradlew :tools:scansuite:run [--args="--styles scan,grayscale --simplified --only <id-substring>"]`
 * Writes build/scansuite/report.{md,json} and outputs/; never fails the build (the gate is the test).
 */
fun main(args: Array<String>) {
    val rootDir = File(System.getProperty("user.dir"))
    val outputDir = File(rootDir, "build/scansuite")
    val styles =
        args
            .getOrNull(args.indexOf("--styles") + 1)
            ?.takeIf { args.contains("--styles") }
            ?.split(",")
            ?.map { name -> OutputStyle.entries.first { it.name.equals(name, ignoreCase = true) } }
            ?.toSet()
            ?: setOf(OutputStyle.Scan)
    val capability = if (args.contains("--simplified")) DeviceCapability.Simplified else DeviceCapability.Full
    args.getOrNull(args.indexOf("--photos") + 1)?.takeIf { args.contains("--photos") }?.let { dir ->
        AdHoc.run(File(dir), File(outputDir, "adhoc"), capability)
        return
    }
    val only = args.getOrNull(args.indexOf("--only") + 1)?.takeIf { args.contains("--only") }
    val runner = SuiteRunner(outputDir, capability, styles, legibility = Tesseract.ifAvailable())
    val sources =
        SuiteRunner.defaultSources(rootDir, outputDir).map { source ->
            if (only ==
                null
            ) {
                source
            } else {
                source.copy(
                    manifest =
                        source.manifest.copy(
                            photos =
                                source.manifest.photos.filter {
                                    only in
                                        it.id
                                },
                        ),
                )
            }
        }
    val report = runner.run(sources)
    println(report.toMarkdown())
    println("Report written to ${File(outputDir, "report.md")}")
}
