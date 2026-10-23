import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/** Applied at the root; configures detekt for every subproject (all `src/<sourceSet>/kotlin`). */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            val configFile = rootProject.file("config/detekt/detekt.yml")
            allprojects {
                pluginManager.apply("io.gitlab.arturbosch.detekt")
                extensions.configure<DetektExtension> {
                    buildUponDefaultConfig = true
                    parallel = true
                    config.setFrom(configFile)
                    source.setFrom(
                        files(
                            "src/commonMain/kotlin",
                            "src/androidMain/kotlin",
                            "src/iosMain/kotlin",
                            "src/commonTest/kotlin",
                            "src/main/kotlin",
                            "src/main/java",
                        ),
                    )
                }
                tasks.withType<Detekt>().configureEach {
                    jvmTarget = "17"
                    reports {
                        sarif.required.set(true)
                        html.required.set(true)
                        xml.required.set(false)
                        txt.required.set(false)
                    }
                }
            }
        }
}
