plugins {
    alias(libs.plugins.homecontrol.android.feature)
    alias(libs.plugins.homecontrol.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.homecontrol.feature.cameras"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.ui)
}
