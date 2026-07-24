// Root build file.
//
// All Android/Kotlin/Compose *configuration* lives in build-logic/convention
// as precompiled convention plugins (applied per-module via the homecontrol.*
// plugin IDs). The `apply false` declarations below are still required, even
// though no module applies these plugin IDs directly: they make Gradle
// resolve the real AGP/Kotlin/KSP plugin artifacts once at the root, so
// that our convention plugins — which only depend on them `compileOnly` — can
// find those classes on the runtime classpath when they call
// `pluginManager.apply(...)` on individual modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
