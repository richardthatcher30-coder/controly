plugins {
    alias(libs.plugins.homecontrol.android.library)
    alias(libs.plugins.homecontrol.android.hilt)
}

android {
    namespace = "com.homecontrol.plugins.samsungtv"
}

dependencies {
    api(project(":core:plugin-api"))
    implementation(libs.kotlinx.coroutines.android)
}
