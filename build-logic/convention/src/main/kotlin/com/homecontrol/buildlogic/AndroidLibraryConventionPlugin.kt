package com.homecontrol.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Applied to every Android library module: `core:*` modules that need the
 * Android framework (database, network, discovery, security, ui, designsystem)
 * and every `plugins:*` / `features:*` module.
 *
 * Kotlin support comes from AGP's built-in Kotlin (AGP 9+) — no separate
 * `org.jetbrains.kotlin.android` plugin is applied, and jvmTarget is left to
 * default from [LibraryExtension.compileOptions.targetCompatibility] below.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk = 36

                defaultConfig {
                    minSdk = 26
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }
    }
}
