plugins {
    alias(libs.plugins.homecontrol.android.library)
}

android {
    namespace = "com.homecontrol.plugins.androidtv"
}

dependencies {
    api(project(":core:plugin-api"))
    implementation(project(":core:security"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
}
