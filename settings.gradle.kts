import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Controly"

// Modules are wired in here only once they have real Gradle build files and
// source — see docs/ARCHITECTURE.md for the full target module layout.
// This milestone: build-logic + the plugin contract modules.
include(":core:model")
include(":core:plugin-api")
include(":core:designsystem")
include(":core:discovery")
include(":core:security")
include(":core:database")
include(":core:data")
include(":plugins:plugin-androidtv")
include(":plugins:plugin-windows")
include(":plugins:plugin-sonytv")
include(":plugins:plugin-samsungtv")
include(":plugins:plugin-registry")
include(":features:feature-splash")
include(":features:feature-dashboard")
include(":features:feature-devices")
include(":features:feature-remote")
include(":features:feature-cameras")
include(":app")
