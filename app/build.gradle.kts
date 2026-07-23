plugins {
    alias(libs.plugins.homecontrol.android.application)
    alias(libs.plugins.homecontrol.android.compose)
    alias(libs.plugins.homecontrol.android.hilt)
}

android {
    namespace = "com.homecontrol.app"

    defaultConfig {
        applicationId = "com.homecontrol.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
}
