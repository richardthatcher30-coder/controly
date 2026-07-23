package com.homecontrol.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Applied to modules that are deliberately plain Kotlin/JVM — no Android
 * framework dependency at all. `core:model` and `core:plugin-api` use this
 * so the build itself enforces that the domain contract never depends on
 * `android.*` classes.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("java-library")

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(17)
            }

            dependencies {
                add("testImplementation", libs.findLibrary("junit").get())
            }
        }
    }
}
