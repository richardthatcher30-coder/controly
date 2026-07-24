plugins {
    alias(libs.plugins.homecontrol.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.homecontrol.plugins.sonytv"
}

dependencies {
    api(project(":core:plugin-api"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.koin.android)
}
