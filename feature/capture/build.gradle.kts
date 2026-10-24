plugins {
    alias(libs.plugins.plainpaper.kmp.feature)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Instrumented tests: Compose semantics / RTL checks (SC-010) and the soak test (SC-008).
    androidLibrary {
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.capture)
            implementation(projects.core.scanengine)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.ui.backhandler)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.junit4)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
