plugins {
    alias(libs.plugins.plainpaper.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
