import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import java.io.File

plugins {
    alias(libs.plugins.plainpaper.kmp.library)
}

val opencvVersion = libs.versions.opencv.get()
val nativeDir = layout.projectDirectory.dir("native")

// ---------------------------------------------------------------------------------------------
// OpenCV for iOS: prebuilt xcframework (research R1); consumed by the iOS CMake builds and
// linked by the Xcode app / Kotlin test binaries.
// ---------------------------------------------------------------------------------------------
val downloadOpencvIosXcframework by tasks.registering(DownloadArchiveTask::class) {
    url.set("https://github.com/yeatse/opencv-spm/releases/download/$opencvVersion/opencv2.xcframework.zip")
    sha256.set(libs.versions.opencvIosXcframeworkSha256.get())
    archiveName.set("opencv2-$opencvVersion-xcframework.zip")
    outputDir.set(layout.buildDirectory.dir("opencv/ios"))
}
val iosXcframeworkPath =
    layout.buildDirectory
        .dir("opencv/ios/build/opencv2.xcframework")
        .get()
        .asFile.absolutePath

val isMacHost = System.getProperty("os.name").lowercase().startsWith("mac")

/** True when an iOS SDK is available (Xcode installed). Without it, iOS native work is skipped. */
val hasXcode: Boolean =
    isMacHost && File("/usr/bin/xcrun").exists() &&
        providers
            .exec {
                commandLine("/usr/bin/xcrun", "--sdk", "iphoneos", "--show-sdk-path")
                isIgnoreExitValue = true
            }.result
            .map { it.exitValue == 0 }
            .get()

fun registerIosNativeBuild(taskName: String, sysroot: String, outputName: String) =
    tasks.register<CMakeBuildTask>(taskName) {
        val xcodeInstalled = hasXcode // captured as a value: the configuration cache cannot serialise the script
        onlyIf("Xcode is installed") { xcodeInstalled }
        dependsOn(downloadOpencvIosXcframework)
        sourceDir.set(nativeDir)
        buildDir.set(layout.buildDirectory.dir("native/$outputName"))
        defines.set(
            mapOf(
                "PP_PLATFORM" to "ios",
                "CMAKE_SYSTEM_NAME" to "iOS",
                "CMAKE_OSX_SYSROOT" to sysroot,
                "CMAKE_OSX_ARCHITECTURES" to "arm64",
                "CMAKE_OSX_DEPLOYMENT_TARGET" to "15.0",
                "PP_OPENCV_XCFRAMEWORK" to iosXcframeworkPath,
            ),
        )
        extraInputs.set(listOf(iosXcframeworkPath))
    }

val buildNativeIosArm64 = registerIosNativeBuild("buildNativeIosArm64", "iphoneos", "ios-arm64")
val buildNativeIosSimulatorArm64 =
    registerIosNativeBuild("buildNativeIosSimulatorArm64", "iphonesimulator", "ios-simulator-arm64")

// ---------------------------------------------------------------------------------------------
// Host build (JVM) for the reference quality suite (research R4).
// ---------------------------------------------------------------------------------------------
val hostOs = if (isMacHost) "darwin" else "linux"
val hostArch =
    when (val arch = System.getProperty("os.arch")) {
        "aarch64" -> "arm64"
        "amd64" -> "x86_64"
        else -> arch
    }
val hostOpencvDir = File(System.getProperty("user.home"), ".cache/plainpaper/opencv-$opencvVersion-$hostOs-$hostArch")

val buildOpencvHost by tasks.registering(Exec::class) {
    description = "Builds the minimal static OpenCV for the host once (scripts/build-opencv-host.sh)."
    val script = rootProject.layout.projectDirectory.file("scripts/build-opencv-host.sh")
    inputs.file(script)
    outputs.dir(hostOpencvDir)
    commandLine(script.asFile.absolutePath)
}

val buildNativeHost by tasks.registering(CMakeBuildTask::class) {
    description = "Builds libplainpaper_scan for the host JVM (quality suite)."
    dependsOn(buildOpencvHost)
    sourceDir.set(nativeDir)
    buildDir.set(layout.buildDirectory.dir("native/host"))
    defines.set(
        mapOf(
            "PP_PLATFORM" to "host",
            "OpenCV_DIR" to File(hostOpencvDir, "lib/cmake/opencv4").absolutePath,
        ),
    )
    extraInputs.set(listOf(hostOpencvDir.absolutePath))
}

// ---------------------------------------------------------------------------------------------
// Kotlin targets
// ---------------------------------------------------------------------------------------------
kotlin {
    jvm()
    // Custom dependsOn edges below would otherwise disable the default template (and with it
    // the shared iosMain source set); applying it explicitly keeps both.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmCommonMain by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmCommonMain)
        jvmMain.get().dependsOn(jvmCommonMain)

        commonMain.dependencies {
            api(projects.core.domain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(projects.core.scanengineAndroid)
            implementation(libs.androidx.exifinterface)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        val simulator = konanTarget.name.contains("simulator")
        val nativeBuild = if (simulator) buildNativeIosSimulatorArm64 else buildNativeIosArm64
        val slice = if (simulator) "ios-arm64_x86_64-simulator" else "ios-arm64"
        val libraryDir =
            nativeBuild
                .get()
                .buildDir
                .get()
                .asFile.absolutePath

        compilations.getByName("main").cinterops.create("plainpaper_scan") {
            definitionFile.set(file("src/nativeInterop/cinterop/plainpaper_scan.def"))
            includeDirs(nativeDir.dir("include"))
            extraOpts("-libraryPath", libraryDir, "-staticLibrary", "libplainpaper_scan.a")
        }

        binaries.withType<TestExecutable>().configureEach {
            linkerOpts(
                "-F$iosXcframeworkPath/$slice",
                "-framework",
                "opencv2",
                "-framework",
                "Accelerate",
                "-framework",
                "CoreImage",
                "-framework",
                "CoreMedia",
                "-framework",
                "CoreVideo",
                "-framework",
                "AVFoundation",
                "-lc++",
            )
        }
    }
}

val xcodeInstalled = hasXcode
tasks.withType<CInteropProcess>().configureEach {
    onlyIf("Xcode is installed") { xcodeInstalled }
    dependsOn(if (konanTarget.name.contains("simulator")) buildNativeIosSimulatorArm64 else buildNativeIosArm64)
}
