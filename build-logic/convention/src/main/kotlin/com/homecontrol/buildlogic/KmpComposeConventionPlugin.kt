package com.homecontrol.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.ComposeExtension

/**
 * Applied to `core:designsystem`, every `features:*` module, and `:ios`, once
 * they carry Compose UI shared between Android and iOS. Replaces
 * `homecontrol.android.compose` — that convention pulls in AndroidX Compose
 * (Google's Maven coordinates, Android-only artifacts); this one pulls in
 * Compose *Multiplatform* (JetBrains' `org.jetbrains.compose`, which publishes
 * the same Material3/Foundation/UI API surface for JVM, Android, and iOS from
 * one dependency declaration — `compose.material3` etc. below, not
 * `androidx.compose.material3:material3`).
 */
class KmpComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<KotlinMultiplatformExtension> {
                val composeExtension = extensions.getByType(ComposeExtension::class.java)

                sourceSets.commonMain.dependencies {
                    implementation(composeExtension.dependencies.runtime)
                    implementation(composeExtension.dependencies.foundation)
                    implementation(composeExtension.dependencies.material3)
                    implementation(composeExtension.dependencies.materialIconsExtended)
                    implementation(composeExtension.dependencies.ui)
                    implementation(composeExtension.dependencies.components.resources)
                }
            }
        }
    }
}
