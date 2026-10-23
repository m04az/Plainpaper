import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Feature module: Compose UI + ViewModel/lifecycle + Koin, depending only on
 * :core:domain and :core:designsystem (constitution Principle III).
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("plainpaper.kmp.compose")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    implementation(project(":core:domain"))
                    implementation(project(":core:designsystem"))
                    implementation(libs.findBundle("lifecycle").get())
                    implementation(libs.findBundle("koin").get())
                }
                sourceSets.commonTest.dependencies {
                    implementation(project(":core:testing"))
                }
            }
        }
}
