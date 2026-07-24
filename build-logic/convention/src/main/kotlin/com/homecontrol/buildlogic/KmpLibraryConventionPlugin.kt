package com.homecontrol.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applied to every module shared between Android and iOS: replaces
 * `homecontrol.jvm.library` and `homecontrol.android.library` for any module
 * that has gained an iOS target as part of the Kotlin Multiplatform port.
 *
 * Uses `com.android.kotlin.multiplatform.library`, not the classic
 * `com.android.library` + `org.jetbrains.kotlin.multiplatform` combination —
 * confirmed via a real build failure that AGP 9 explicitly rejects that
 * combination ("The 'com.android.library' ... plugin is not compatible with
 * the 'org.jetbrains.kotlin.multiplatform' plugin since AGP 9.0") and directs
 * to this dedicated plugin instead. Its Android-specific settings
 * (namespace/compileSdk/minSdk) live inside `kotlin { androidLibrary { ... } }`
 * in a `.gradle.kts` script — but that's a *dynamically* Gradle-registered
 * extension (named "androidLibrary" on `KotlinMultiplatformExtension`), not a
 * real Kotlin extension function, so it isn't visible to a precompiled `.kt`
 * plugin class like this one (confirmed via a second real build failure:
 * "Unresolved reference 'androidLibrary'", then verified by inspecting AGP's
 * own jar — the extension is `extensions.create("androidLibrary", ...)`, not
 * a compile-time accessor). Accessed here via plain `ExtensionAware` instead.
 * Each module still sets its own `namespace` there since that's per-module.
 *
 * Every KMP module consumed by the Android `app` module needs this real
 * Android target (not just a plain `jvm()`) for AGP's variant-aware
 * dependency resolution to line up — true even for modules like `core:model`
 * that have zero `android.*` API usage. That's why this single plugin covers
 * both "pure logic" and "needs real Android platform APIs in androidMain"
 * modules identically: the Gradle-level wiring is the same either way, only
 * the module's own `iosMain`/`androidMain` source content differs.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")

            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure(KotlinMultiplatformAndroidLibraryExtension::class.java) {
                    compileSdk = 36
                    minSdk = 26
                }
                iosArm64()
                iosSimulatorArm64()

                sourceSets.commonTest.dependencies {
                    implementation(kotlin("test"))
                }

                // expect/actual classes (SecureKeyStorage, TcpSocket, ...) are
                // still Beta in Kotlin 2.3.20 and warn on every compile without this.
                targets.configureEach {
                    compilations.configureEach {
                        compileTaskProvider.configure {
                            compilerOptions {
                                freeCompilerArgs.add("-Xexpect-actual-classes")
                            }
                        }
                    }
                }
            }
        }
    }
}
