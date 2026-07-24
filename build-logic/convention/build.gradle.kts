plugins {
    `kotlin-dsl`
}

group = "com.homecontrol.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "homecontrol.android.application"
            implementationClass = "com.homecontrol.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "homecontrol.android.library"
            implementationClass = "com.homecontrol.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "homecontrol.android.compose"
            implementationClass = "com.homecontrol.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "homecontrol.android.feature"
            implementationClass = "com.homecontrol.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "homecontrol.jvm.library"
            implementationClass = "com.homecontrol.buildlogic.JvmLibraryConventionPlugin"
        }
        register("kmpLibrary") {
            id = "homecontrol.kmp.library"
            implementationClass = "com.homecontrol.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "homecontrol.kmp.compose"
            implementationClass = "com.homecontrol.buildlogic.KmpComposeConventionPlugin"
        }
    }
}
