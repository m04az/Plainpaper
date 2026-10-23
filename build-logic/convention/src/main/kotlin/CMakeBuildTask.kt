import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Configures and builds a CMake project with Ninja. `cmake` is looked up on `PATH`, then in
 * `CMAKE_HOME`, then in the Android SDK (`$ANDROID_HOME/cmake/<version>/bin`, which also
 * ships `ninja`). Used for the host and iOS builds of the scan engine (research R3, R4);
 * Android uses AGP's own `externalNativeBuild`.
 */
abstract class CMakeBuildTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:Input
    abstract val defines: MapProperty<String, String>

    @get:Input
    abstract val buildType: Property<String>

    /** Extra inputs that are not files under [sourceDir], e.g. the OpenCV install path. */
    @get:Input
    abstract val extraInputs: ListProperty<String>

    @get:OutputDirectory
    abstract val buildDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        buildType.convention("Release")
        extraInputs.convention(emptyList())
    }

    @TaskAction
    fun run() {
        val (cmake, ninja) = locateTools()
        val build = buildDir.get().asFile.apply { mkdirs() }
        val args =
            mutableListOf(
                cmake.absolutePath,
                "-S",
                sourceDir.get().asFile.absolutePath,
                "-B",
                build.absolutePath,
                "-G",
                "Ninja",
                "-DCMAKE_BUILD_TYPE=${buildType.get()}",
            )
        ninja?.let { args += "-DCMAKE_MAKE_PROGRAM=${it.absolutePath}" }
        defines.get().forEach { (key, value) -> args += "-D$key=$value" }
        execOperations.exec { commandLine(args) }
        execOperations.exec { commandLine(cmake.absolutePath, "--build", build.absolutePath, "--parallel") }
    }

    private fun locateTools(): Pair<File, File?> {
        val onPath = findOnPath("cmake")
        if (onPath != null) return onPath to findOnPath("ninja")
        System.getenv("CMAKE_HOME")?.let { home ->
            val candidate = File(home, "bin/cmake")
            if (candidate.canExecute()) return candidate to File(home, "bin/ninja").takeIf { it.canExecute() }
        }
        val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: androidSdkFromLocalProperties()
        val sdkCmake =
            sdk
                ?.let { File(it, "cmake") }
                ?.listFiles()
                ?.filter { File(it, "bin/cmake").canExecute() }
                ?.maxByOrNull { it.name }
        if (sdkCmake != null) {
            return File(sdkCmake, "bin/cmake") to File(sdkCmake, "bin/ninja").takeIf { it.canExecute() }
        }
        throw GradleException(
            "cmake not found. Install it (brew install cmake ninja) or install the CMake package in the Android SDK.",
        )
    }

    private fun androidSdkFromLocalProperties(): String? {
        var dir: File? = sourceDir.get().asFile
        while (dir != null) {
            val props = File(dir, "local.properties")
            if (props.isFile) {
                return props
                    .readLines()
                    .firstOrNull { it.startsWith("sdk.dir=") }
                    ?.substringAfter("=")
                    ?.replace("\\:", ":")
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun findOnPath(name: String): File? =
        System
            .getenv("PATH")
            ?.split(File.pathSeparator)
            ?.map { File(it, name) }
            ?.firstOrNull { it.canExecute() }
}
