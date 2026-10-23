import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Shared configuration for every Kotlin Multiplatform module: Android (KMP library plugin,
 * AGP 9 split, research R2), iOS arm64 + simulator arm64, explicit API for core modules,
 * common test dependencies, and warnings-as-errors on CI.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")

            extensions.configure<KotlinMultiplatformExtension> {
                val kotlinExtensions = (this as ExtensionAware).extensions
                kotlinExtensions.configure<KotlinMultiplatformAndroidLibraryTarget>("androidLibrary") {
                    namespace = plainpaperNamespace
                    compileSdk = libs.version("android-compileSdk").toInt()
                    minSdk = libs.version("android-minSdk").toInt()
                    androidResources {
                        enable = true
                    }
                    // Mandatory: without host tests commonTest never runs on the JVM (tasks T005).
                    withHostTestBuilder {}
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }

                iosArm64()
                iosSimulatorArm64()

                if (isCoreModule) {
                    explicitApi()
                }

                compilerOptions {
                    allWarningsAsErrors.set(isCi)
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }

                sourceSets.commonTest.dependencies {
                    implementation(kotlin("test"))
                    implementation(libs.findLibrary("kotlinx-coroutines-test").get())
                    implementation(libs.findLibrary("turbine").get())
                }
            }
        }
}
