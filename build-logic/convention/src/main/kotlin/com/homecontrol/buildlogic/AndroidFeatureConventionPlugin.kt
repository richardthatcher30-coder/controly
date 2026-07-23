package com.homecontrol.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Applied to every module under `features/`. Adds the plumbing every
 * feature screen needs — Compose navigation and ViewModel-Compose interop.
 * Project-specific dependencies (which `core:*` modules a given feature
 * actually needs) stay in that feature's own build.gradle.kts.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("homecontrol.android.library")
            pluginManager.apply("homecontrol.android.compose")

            dependencies {
                add("implementation", libs.findLibrary("androidx-navigation-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-ktx").get())
            }
        }
    }
}
