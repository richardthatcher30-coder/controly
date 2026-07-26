import java.util.Properties

plugins {
    alias(libs.plugins.homecontrol.android.application)
    alias(libs.plugins.homecontrol.android.compose)
}

// Release signing lives outside version control — see /keystore/keystore.properties
// (gitignored). Falls back to unsigned if that file isn't present, so a fresh
// checkout without it can still build debug/assembleRelease for local testing.
val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.homecontrol.app"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.controly.app"
        versionCode = 9
        versionName = "0.1.7"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file("keystore/${keystoreProperties["storeFile"]}")
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            ndk {
                // Bundles native debug symbols into the AAB itself so Play Console can
                // symbolicate native crashes/ANRs without a separate manual upload.
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":features:feature-splash"))
    implementation(project(":features:feature-dashboard"))
    implementation(project(":features:feature-devices"))
    implementation(project(":features:feature-remote"))
    implementation(project(":features:feature-cameras"))
    implementation(project(":plugins:plugin-registry"))

    // Hilt used to wire these transitively through its own aggregating
    // annotation processor; Koin modules are plain top-level vals, so
    // HomeControlApplication.kt needs a real Gradle dependency edge on every
    // module whose Koin module it imports directly.
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:discovery"))
    implementation(project(":core:security"))
    implementation(project(":plugins:plugin-androidtv"))
    implementation(project(":plugins:plugin-samsungtv"))
    implementation(project(":plugins:plugin-sonytv"))
    implementation(project(":plugins:plugin-windows"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.koin.android)
}
