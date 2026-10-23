package app.plainpaper.scansuite

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import java.io.File

/**
 * `./gradlew :tools:scansuite:run [--args="--styles scan,grayscale --simplified"]`
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
    val runner = SuiteRunner(outputDir, capability, styles, legibility = Tesseract.ifAvailable())
    val report = runner.run(SuiteRunner.defaultSources(rootDir, outputDir))
    println(report.toMarkdown())
    println("Report written to ${File(outputDir, "report.md")}")
}
