plugins {
    alias(libs.plugins.plainpaper.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(libs.datastore.preferences.core)
            implementation(libs.okio)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
