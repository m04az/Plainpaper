plugins {
    alias(libs.plugins.plainpaper.kmp.compose)
}

kotlin {
    // Instrumented tests: the CameraX smoke test on the emulator's virtual-scene camera (R13).
    androidLibrary {
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            api(projects.core.scanengine)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.bundles.camerax)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.play.services.mlkit.document.scanner)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.junit4)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
