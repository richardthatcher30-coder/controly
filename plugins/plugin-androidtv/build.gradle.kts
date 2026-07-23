plugins {
    alias(libs.plugins.homecontrol.android.library)
    alias(libs.plugins.homecontrol.android.hilt)
}

android {
    namespace = "com.homecontrol.plugins.androidtv"
}

dependencies {
    api(project(":core:plugin-api"))
    implementation(project(":core:security"))
    implementation(libs.kotlinx.coroutines.android)
}
