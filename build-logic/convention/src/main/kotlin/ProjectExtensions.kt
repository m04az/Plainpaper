import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

/** `:core:domain` -> `app.plainpaper.core.domain` */
internal val Project.plainpaperNamespace: String
    get() = "app.plainpaper" + path.replace(':', '.').replace('-', '_')

internal val Project.isCoreModule: Boolean
    get() = path.startsWith(":core:")

internal val Project.isCi: Boolean
    get() = providers.gradleProperty("ci").map { it.toBoolean() }.getOrElse(false)
