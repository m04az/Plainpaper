plugins {
    alias(libs.plugins.plainpaper.kmp.library)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(libs.datastore.preferences.core)
            implementation(libs.okio)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.okio.fakefilesystem)
        }
    }
}
