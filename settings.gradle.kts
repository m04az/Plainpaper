pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Plainpaper"

include(":androidApp")
include(":composeApp")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:testing")
include(":feature:home")
include(":feature:settings")

// Reserved for later features (see constitution Principle IX: created only when needed):
// include(":core:database")   // Feature 6 - Document library (Room KMP)
// include(":core:scanengine") // Feature 2 - Scan engine (OpenCV C++ core)
