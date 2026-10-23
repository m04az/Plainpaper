import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Applied at the root: merged Kover report over domain + presentation modules with the
 * constitution's 80% line-coverage gate (contracts/ci.md "Coverage gate").
 */
class KoverConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            val measured = listOf(":core:domain", ":core:scanengine", ":feature:home", ":feature:settings")
            measured.forEach { path ->
                project(path).pluginManager.apply("org.jetbrains.kotlinx.kover")
            }

            dependencies {
                measured.forEach { path -> add("kover", project(path)) }
            }

            extensions.configure<KoverProjectExtension> {
                reports {
                    filters {
                        excludes {
                            // UI is verified on device, not by line coverage.
                            annotatedBy("androidx.compose.runtime.Composable")
                            classes(
                                "*Sheet*",
                                "*PickerOption*",
                                "*Screen*",
                                "*Route*",
                                "*Preview*",
                                "*ComposableSingletons*",
                                "*Module*Kt",
                                "*.resources.*",
                                "*Res",
                                "*Res\$*",
                                "*\$\$serializer",
                                // Scan engine: platform bridges, decoders and native wrappers are
                                // exercised by the reference suite and on devices, not by line coverage.
                                "*.scanengine.bridge.*",
                                "*PhotoDecoder*",
                                "*CapabilityProvider*",
                                "*Jni*",
                                "*Cinterop*",
                                "*ScanPhoto*",
                                "*ScanOutput*",
                                "*NativeLoader*",
                            )
                            androidGeneratedClasses()
                        }
                    }
                    total {
                        verify {
                            rule("Domain and presentation line coverage must be at least 80% (constitution VI)") {
                                minBound(80)
                            }
                        }
                    }
                }
            }
        }
}
