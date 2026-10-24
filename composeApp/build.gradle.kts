plugins {
    alias(libs.plugins.plainpaper.kmp.compose)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.scanengine)
            implementation(projects.core.capture)
            implementation(projects.core.designsystem)
            implementation(projects.feature.home)
            implementation(projects.feature.settings)
            implementation(projects.feature.capture)
            implementation(libs.bundles.lifecycle)
            implementation(libs.bundles.koin)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
