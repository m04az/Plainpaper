plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

description =
    "Reference-photo quality suite for the scan engine (specs/002-scan-engine). Developer tooling, never shipped."

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(providers.gradleProperty("ci").map { it.toBoolean() }.getOrElse(false))
    }
}

application {
    mainClass.set("app.plainpaper.scansuite.MainKt")
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.scanengine)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

val hostNativeLibrary = project(":core:scanengine").tasks.named("buildNativeHost")

/** Absolute path of libplainpaper_scan.{dylib|so} built for the host (research R4). */
fun hostLibraryPath(): String {
    val os = System.getProperty("os.name").lowercase()
    val ext = if (os.startsWith("mac")) "dylib" else "so"
    return rootProject.layout.projectDirectory
        .file("core/scanengine/build/native/host/libplainpaper_scan.$ext")
        .asFile.absolutePath
}

tasks.withType<JavaExec>().configureEach {
    dependsOn(hostNativeLibrary)
    workingDir = rootDir
    systemProperty("pp.native.path", hostLibraryPath())
    systemProperty("java.awt.headless", "true")
    jvmArgs("-Xmx2g")
}

tasks.test {
    dependsOn(hostNativeLibrary)
    useJUnitPlatform()
    workingDir = rootDir
    systemProperty("pp.native.path", hostLibraryPath())
    systemProperty("java.awt.headless", "true")
    environment("PP_REQUIRE_OCR", System.getenv("PP_REQUIRE_OCR") ?: "0")
    maxHeapSize = "2g"
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}
