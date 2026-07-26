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
 * Depends on the four modules that are genuinely KMP today (`core:model`,
 * `core:net-io`, `core:crypto`) for shared data types, sockets, and ADB
 * signing/Keychain storage. Everything UI-facing (`core:designsystem`,
 * `core:data`/`core:database`/`core:discovery`, the device plugins, and all
 * `feature-*` modules) is still Android-only, so this module's own Dashboard/
 * About/Devices/Pairing screens and its ADB pairing implementation are
 * iOS-native code, not shared with Android yet — see the "fast iOS-native
 * path" decision in the Dashboard/Devices build task. `feature-*`/
 * `plugin-registry` dependencies get added once those modules are themselves
 * converted to KMP (a later, separate effort) and this module's screens can
 * be unified with Android's.
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
        implementation(project(":core:model"))
        implementation(project(":core:net-io"))
        implementation(project(":core:crypto"))
        implementation(project(":core:companion-protocol"))
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.websockets)
    }

    sourceSets.iosMain.dependencies {
        implementation(libs.ktor.client.darwin)
        // Only for the WS-Security SHA-1 digest ONVIF camera auth needs
        // (see cameras/onvif/OnvifClient.kt) -- cryptography-core itself is
        // already transitively visible via core:companion-protocol's `api`
        // dependency on it, but the actual Apple provider implementation
        // (CryptographyProvider.Apple) is only `implementation` there, so it
        // needs its own declaration here to be usable directly.
        implementation(libs.cryptography.provider.apple)
    }
}
