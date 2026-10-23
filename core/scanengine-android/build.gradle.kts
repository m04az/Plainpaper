plugins {
    alias(libs.plugins.androidLibrary)
}

// The AGP 9 Kotlin Multiplatform library plugin cannot run externalNativeBuild, so this plain
// Android library owns the NDK build of the shared C++ core (research R2). No Kotlin sources.

val opencvVersion = libs.versions.opencv.get()

val downloadOpencvAndroidSdk by tasks.registering(DownloadArchiveTask::class) {
    url.set("https://github.com/opencv/opencv/releases/download/$opencvVersion/opencv-$opencvVersion-android-sdk.zip")
    sha256.set(libs.versions.opencvAndroidSdkSha256.get())
    archiveName.set("opencv-$opencvVersion-android-sdk.zip")
    outputDir.set(layout.buildDirectory.dir("opencv"))
}

val opencvJniDir = layout.buildDirectory.dir("opencv/OpenCV-android-sdk/sdk/native/jni")

android {
    namespace = "app.plainpaper.core.scanengine.ndk"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    ndkVersion = libs.versions.androidNdk.get()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments +=
                    listOf(
                        "-DPP_PLATFORM=android",
                        "-DOpenCV_DIR=${opencvJniDir.get().asFile.absolutePath}",
                        "-DANDROID_STL=c++_static",
                        "-DANDROID_ARM_NEON=ON",
                    )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../scanengine/native/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) {
        dependsOn(downloadOpencvAndroidSdk)
    }
}
