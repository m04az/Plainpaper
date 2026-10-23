import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Applied at the root: ktlint (ktlint_official via .editorconfig) over every Kotlin file. */
class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.diffplug.spotless")
            val ktlintVersion = libs.version("ktlint")

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("**/*.kt")
                    targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", ".idea/**", "iosApp/**")
                    ktlint(ktlintVersion)
                    trimTrailingWhitespace()
                    endWithNewline()
                }
                kotlinGradle {
                    target("**/*.gradle.kts")
                    targetExclude("**/build/**", "**/.gradle/**")
                    ktlint(ktlintVersion)
                    trimTrailingWhitespace()
                    endWithNewline()
                }
                format("misc") {
                    target("**/*.md", "**/*.yml", "**/*.yaml", "**/*.xml", ".gitignore", ".editorconfig")
                    targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", ".idea/**", "iosApp/**")
                    trimTrailingWhitespace()
                    endWithNewline()
                }
            }
        }
}
