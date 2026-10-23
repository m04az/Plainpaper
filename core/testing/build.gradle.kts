plugins {
    alias(libs.plugins.plainpaper.kmp.library)
}

kotlin {
    // Test support code is consumed by tests only; explicit API is not useful here.
    explicitApi = null

    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            api(libs.kotlinx.coroutines.test)
            api(libs.turbine)
            api(kotlin("test"))
        }
    }
}
