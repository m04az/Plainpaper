plugins {
    `kotlin-dsl`
}

group = "app.plainpaper.buildlogic"

dependencies {
    compileOnly(libs.plugin.kotlin)
    compileOnly(libs.plugin.kotlin.compose)
    compileOnly(libs.plugin.android)
    compileOnly(libs.plugin.compose)
    compileOnly(libs.plugin.detekt)
    compileOnly(libs.plugin.spotless)
    compileOnly(libs.plugin.kover)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "plainpaper.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "plainpaper.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("kmpFeature") {
            id = "plainpaper.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
        register("spotless") {
            id = "plainpaper.spotless"
            implementationClass = "SpotlessConventionPlugin"
        }
        register("detekt") {
            id = "plainpaper.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("kover") {
            id = "plainpaper.kover"
            implementationClass = "KoverConventionPlugin"
        }
    }
}
