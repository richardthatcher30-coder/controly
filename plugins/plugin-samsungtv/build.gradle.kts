plugins {
    alias(libs.plugins.homecontrol.android.library)
}

android {
    namespace = "com.homecontrol.plugins.samsungtv"
}

dependencies {
    api(project(":core:plugin-api"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
}
