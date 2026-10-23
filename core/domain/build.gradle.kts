plugins {
    alias(libs.plugins.plainpaper.kmp.library)
}

kotlin {
    // The host JVM target serves the scan-engine quality suite (specs/002, research R4).
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
