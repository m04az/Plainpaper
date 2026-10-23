import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * KMP library + Compose Multiplatform UI + resources (research R1, R8).
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("plainpaper.kmp.library")
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val compose = extensions.getByType<ComposeExtension>().dependencies

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    implementation(compose.runtime)
                    implementation(compose.foundation)
                    implementation(compose.material3)
                    implementation(compose.ui)
                    implementation(compose.components.resources)
                    implementation(compose.components.uiToolingPreview)
                }
            }

            dependencies {
                add("androidRuntimeClasspath", compose.uiTooling)
            }

            (extensions.getByType<ComposeExtension>() as ExtensionAware).extensions.configure<ResourcesExtension>(
                "resources",
            ) {
                publicResClass = false
                packageOfResClass = "$plainpaperNamespace.resources"
                generateResClass = ResourcesExtension.ResourceClassGeneration.Always
            }
        }
}
