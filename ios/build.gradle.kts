plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The single Kotlin/Native framework-export aggregation point Xcode
 * consumes — mirrors the role `app` plays for Android (composition root, no
 * business logic of its own). Everything else in the dependency graph is a
 * genuine multiplatform module; this module's only job is producing one
 * `.framework`/`.xcframework` from all of them.
 *
 * Phase 1 scaffold only: depends on nothing but Compose Multiplatform itself
 * and shows a placeholder screen. `feature-*`/`plugin-registry` dependencies
 * get added once those modules are themselves converted to KMP (later
 * phases) — adding them now would just fail to compile.
 */
kotlin {
    iosArm64()
    iosSimulatorArm64()
    // Deliberately no iosX64() (Intel simulator) — see KmpLibraryConventionPlugin's
    // doc comment. x86_64 is excluded from the Xcode project's simulator
    // ARCHS instead, so it's never actually requested.

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ControlyShared"
            isStatic = true
        }
    }

    sourceSets.commonMain.dependencies {
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
    }
}
